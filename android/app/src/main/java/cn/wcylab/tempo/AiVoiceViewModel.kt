package cn.wcylab.tempo

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.wcylab.tempo.data.CalendarDraftDto
import cn.wcylab.tempo.data.TempoApiFactory
import cn.wcylab.tempo.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

sealed interface AiVoiceState {
    data object Idle : AiVoiceState
    data object Recording : AiVoiceState
    data object Uploading : AiVoiceState
    data class Ready(val draft: CalendarDraftDto, val transcript: String?) : AiVoiceState
    data class Error(val message: String) : AiVoiceState
}

sealed interface AiAccessState {
    data object Disabled : AiAccessState
    data object Verifying : AiAccessState
    data object Enabled : AiAccessState
    data class Error(val message: String) : AiAccessState
}

class AiVoiceViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val api = TempoApiFactory.create { tokenStore.get() }
    private val _state = MutableStateFlow<AiVoiceState>(AiVoiceState.Idle)
    val state = _state.asStateFlow()
    private val _access = MutableStateFlow<AiAccessState>(if (tokenStore.getAiAccessToken() != null) AiAccessState.Enabled else AiAccessState.Disabled)
    val access = _access.asStateFlow()
    private var recorder: AudioRecord? = null
    private var recordingFile: File? = null
    private var recordingJob: Job? = null
    @Volatile private var recordingActive = false
    private var recordingTimeoutJob: Job? = null

    fun verifyAccess(code: String?) {
        val normalizedCode = code?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            _access.value = AiAccessState.Verifying
            runCatching { api.verifyAiAccess(cn.wcylab.tempo.data.AiAccessVerifyRequestDto(normalizedCode)) }
                .onSuccess { response -> tokenStore.saveAiAccessToken(response.accessToken); _access.value = AiAccessState.Enabled }
                .onFailure { error -> _access.value = AiAccessState.Error(if (error is HttpException && error.code() == 403) "内测密码错误" else "AI 内测验证失败，请稍后重试") }
        }
    }

    fun startRecording() {
        if (_state.value is AiVoiceState.Recording) return
        recordingActive = false
        recordingJob?.cancel()
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        runCatching {
            val file = File(getApplication<Application>().cacheDir, "tempo-voice-${System.currentTimeMillis()}.wav")
            val sampleRate = 16_000
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val activeRecorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 4096))
            activeRecorder.startRecording()
            recorder = activeRecorder
            recordingFile = file
            recordingActive = true
            _state.value = AiVoiceState.Recording
            recordingJob = viewModelScope.launch(Dispatchers.IO) {
                val pcm = ByteArrayOutputStream()
                val buffer = ByteArray(maxOf(minBuffer, 4096))
                try {
                    while (isActive && recordingActive) {
                        val read = activeRecorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                        if (read > 0) pcm.write(buffer, 0, read)
                        else delay(10)
                    }
                } finally {
                    val data = pcm.toByteArray()
                    FileOutputStream(file).use { output ->
                        writeWavHeader(output, data.size, sampleRate, 1, 16)
                        output.write(data)
                    }
                }
            }
            recordingTimeoutJob = viewModelScope.launch {
                delay(30_000)
                if (_state.value is AiVoiceState.Recording) stopRecording()
            }
        }.onFailure { _state.value = AiVoiceState.Error("无法开始录音，请检查麦克风权限") }
    }

    fun stopRecording() {
        val activeRecorder = recorder ?: return
        recordingActive = false
        _state.value = AiVoiceState.Uploading
        recordingTimeoutJob?.cancel()
        recordingTimeoutJob = null
        val file = recordingFile
        recorder = null
        recordingFile = null
        runCatching { activeRecorder.stop() }
        activeRecorder.release()
        val writeJob = recordingJob
        recordingJob = null
        viewModelScope.launch {
            try {
                val completed = withTimeoutOrNull(3_000) {
                    writeJob?.join()
                    true
                } ?: false
                if (!completed) {
                    writeJob?.cancel()
                    writeJob?.join()
                }
                if (file == null || !file.exists() || file.length() <= 44) {
                    _state.value = AiVoiceState.Error("录音失败，请重试")
                    return@launch
                }
                if (tokenStore.get().isNullOrBlank()) {
                    _state.value = AiVoiceState.Error("请先登录后使用语音填写日程")
                    return@launch
                }
                _state.value = AiVoiceState.Uploading
                val aiToken = tokenStore.getAiAccessToken() ?: error("请先在“我的”中完成 AI 内测验证")
                val response = api.parseCalendarAudio(
                    aiToken,
                    MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("audio/wav".toMediaType())),
                    ZoneId.systemDefault().id.toRequestBody("text/plain".toMediaType()),
                    LocalDate.now().toString().toRequestBody("text/plain".toMediaType())
                )
                _state.value = AiVoiceState.Ready(response.draft, response.transcript)
            } catch (error: Exception) {
                _state.value = AiVoiceState.Error(
                    if (error is HttpException && error.code() == 401) "登录已失效，请重新登录后再试"
                    else error.message ?: "AI 解析失败，请稍后重试"
                )
            } finally {
                file?.delete()
            }
        }
    }

    /** 取消当前录音：停止采集、删除临时音频，不上传也不生成草稿。 */
    fun cancelRecording() {
        if (_state.value !is AiVoiceState.Recording) return
        recordingActive = false
        recordingTimeoutJob?.cancel()
        recordingTimeoutJob = null
        val activeRecorder = recorder
        recorder = null
        runCatching { activeRecorder?.stop() }
        activeRecorder?.release()
        val writeJob = recordingJob
        recordingJob = null
        val file = recordingFile
        recordingFile = null
        viewModelScope.launch {
            writeJob?.cancelAndJoin()
            file?.delete()
            _state.value = AiVoiceState.Idle
        }
    }

    fun clear() {
        recordingActive = false
        recordingTimeoutJob?.cancel()
        recordingTimeoutJob = null
        _state.value = AiVoiceState.Idle
    }

    override fun onCleared() {
        recordingActive = false
        recordingTimeoutJob?.cancel()
        recordingJob?.cancel()
        recorder?.let { runCatching { it.stop() }; it.release() }
        recordingFile?.delete()
        super.onCleared()
    }

    private fun writeWavHeader(output: FileOutputStream, dataLength: Int, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        fun writeInt(value: Int) { output.write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())) }
        fun writeShort(value: Int) { output.write(byteArrayOf(value.toByte(), (value shr 8).toByte())) }
        output.write("RIFF".toByteArray()); writeInt(36 + dataLength); output.write("WAVE".toByteArray())
        output.write("fmt ".toByteArray()); writeInt(16); writeShort(1); writeShort(channels); writeInt(sampleRate); writeInt(byteRate); writeShort(blockAlign); writeShort(bitsPerSample)
        output.write("data".toByteArray()); writeInt(dataLength)
    }
}
