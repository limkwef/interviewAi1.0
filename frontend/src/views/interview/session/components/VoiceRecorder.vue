<template>
  <div class="voice-recorder">
    <button
      :class="['record-btn', { recording: isRecording, uploading: isUploading }]"
      @click="toggleRecord"
      :disabled="disabled || isUploading"
      :title="isRecording ? '点击停止录音' : '点击开始录音'"
    >
      <svg v-if="!isRecording && !isUploading" width="20" height="20" viewBox="0 0 24 24"
           fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
        <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
        <line x1="12" y1="19" x2="12" y2="23"/>
        <line x1="8" y1="23" x2="16" y2="23"/>
      </svg>
      <div v-else-if="isRecording" class="recording-indicator">
        <span class="recording-dot"></span>
      </div>
      <svg v-else width="20" height="20" viewBox="0 0 24 24"
           fill="none" stroke="currentColor" stroke-width="2" class="spin-icon">
        <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
      </svg>
    </button>

    <div v-if="isRecording" class="record-status">
      <span class="status-dot"></span>
      <span class="record-time">{{ formatDuration(duration) }}</span>
      <div class="volume-bar-container">
        <div class="volume-bar" v-for="i in 5" :key="i"
             :class="{ active: volume > i * 0.2 - 0.1 }"></div>
      </div>
    </div>

    <div v-if="error" class="record-error">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { AudioRecorder, convertToPcm } from '@/utils/audio'
import { uploadAudio } from '@/api/speech'
import { ElMessage } from 'element-plus'

const props = defineProps({
  disabled: Boolean
})

const emit = defineEmits(['result', 'error'])

const isRecording = ref(false)
const isUploading = ref(false)
const duration = ref(0)
const volume = ref(0)
const error = ref('')

let recorder = null
let durationTimer = null

async function toggleRecord() {
  if (isRecording.value) {
    await stopRecord()
  } else {
    await startRecord()
  }
}

async function startRecord() {
  if (props.disabled) return
  error.value = ''

  try {
    recorder = new AudioRecorder({
      onVolumeChange: (v) => { volume.value = v }
    })
    await recorder.start()
    isRecording.value = true

    duration.value = 0
    durationTimer = setInterval(() => {
      duration.value = recorder.getDuration()
      if (duration.value >= 59) {
        stopRecord()
      }
    }, 200)
  } catch (e) {
    if (e.name === 'NotAllowedError' || e.name === 'PermissionDeniedError') {
      error.value = '麦克风权限被拒绝，请在浏览器设置中允许'
    } else if (e.name === 'NotFoundError') {
      error.value = '未检测到麦克风设备'
    } else {
      error.value = '启动录音失败：' + e.message
    }
    emit('error', error.value)
  }
}

async function stopRecord() {
  if (!recorder || !isRecording.value) return
  clearInterval(durationTimer)
  isRecording.value = false
  isUploading.value = true

  try {
    const audioBlob = await recorder.stop()

    if (audioBlob.size < 100) {
      error.value = '录音太短，请重新录制'
      isUploading.value = false
      return
    }

    const pcmBlob = await convertToPcm(audioBlob)

    emit('result', { status: 'uploading' })
    const text = await uploadAudio(pcmBlob, 'pcm')

    if (text && text.trim()) {
      emit('result', { status: 'success', text: text.trim() })
    } else {
      emit('result', { status: 'error', message: '未能识别到语音内容，请重新录制' })
      ElMessage.warning('未能识别到语音内容')
    }
  } catch (e) {
    emit('result', { status: 'error', message: e.message })
    ElMessage.error('语音识别失败：' + e.message)
  } finally {
    isUploading.value = false
  }
}

function formatDuration(seconds) {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

onBeforeUnmount(() => {
  if (recorder && isRecording.value) {
    recorder.stop()
  }
  clearInterval(durationTimer)
})
</script>

<style scoped>
.voice-recorder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.record-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 2px solid #E4E4E7;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  color: #71717A;
  flex-shrink: 0;
}

.record-btn:hover:not(:disabled) {
  border-color: #2563EB;
  color: #2563EB;
}

.record-btn.recording {
  background: #DC2626;
  border-color: #DC2626;
  color: #fff;
  animation: pulse 1.5s infinite;
}

.record-btn.uploading {
  border-color: #2563EB;
  color: #2563EB;
  cursor: wait;
}

.record-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(220, 38, 38, 0); }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin-icon {
  animation: spin 1s linear infinite;
}

.recording-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
}

.recording-dot {
  width: 10px;
  height: 10px;
  background: #fff;
  border-radius: 50%;
}

.record-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #DC2626;
}

.status-dot {
  width: 6px;
  height: 6px;
  background: #DC2626;
  border-radius: 50%;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.volume-bar-container {
  display: flex;
  gap: 2px;
  align-items: flex-end;
  height: 12px;
}

.volume-bar {
  width: 3px;
  height: 4px;
  background: #D4D4D8;
  border-radius: 1px;
  transition: all 0.1s;
}

.volume-bar.active {
  background: #DC2626;
  height: 12px;
}

.record-error {
  font-size: 11px;
  color: #DC2626;
  max-width: 120px;
  text-align: center;
  line-height: 1.3;
}
</style>
