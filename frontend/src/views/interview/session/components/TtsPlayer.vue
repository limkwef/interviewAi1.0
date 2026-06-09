<template>
  <div class="tts-player" v-if="audioUrl">
    <audio
      ref="audioRef"
      :src="audioUrl"
      @ended="onEnded"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoaded"
      @error="onError"
      preload="metadata"
    ></audio>

    <button class="tts-btn" @click="togglePlay" :disabled="loading">
      <svg v-if="loading" width="14" height="14" viewBox="0 0 24 24"
           fill="none" stroke="currentColor" stroke-width="2" class="spin-icon">
        <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
      </svg>
      <svg v-else-if="!playing" width="14" height="14" viewBox="0 0 24 24"
           fill="currentColor">
        <polygon points="5 3 19 12 5 21 5 3"/>
      </svg>
      <svg v-else width="14" height="14" viewBox="0 0 24 24"
           fill="currentColor">
        <rect x="6" y="4" width="4" height="16"/>
        <rect x="14" y="4" width="4" height="16"/>
      </svg>
    </button>

    <div class="tts-progress" @click="seekAudio" ref="progressRef">
      <div class="tts-progress-fill" :style="{ width: progressPercent + '%' }"></div>
    </div>

    <span class="tts-time">{{ formatAudioTime(currentTime) }}/{{ formatAudioTime(audioDuration) }}</span>
  </div>

  <div class="tts-player tts-player-idle" v-else>
    <button class="tts-btn tts-load-btn" @click="loadAudio" :disabled="loading">
      <svg v-if="loading" width="14" height="14" viewBox="0 0 24 24"
           fill="none" stroke="currentColor" stroke-width="2" class="spin-icon">
        <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
      </svg>
      <svg v-else width="14" height="14" viewBox="0 0 24 24"
           fill="none" stroke="currentColor" stroke-width="2">
        <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
        <path d="M15.54 8.46a5 5 0 0 1 0 7.07"/>
        <path d="M19.07 4.93a10 10 0 0 1 0 14.14"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { fetchTts } from '@/api/speech'

const props = defineProps({
  text: { type: String, required: true },
  autoPlay: { type: Boolean, default: false }
})

const audioRef = ref(null)
const progressRef = ref(null)
const audioUrl = ref('')
const playing = ref(false)
const loading = ref(false)
const currentTime = ref(0)
const audioDuration = ref(0)

async function loadAudio() {
  if (!props.text || loading.value) return
  loading.value = true

  try {
    const blob = await fetchTts(props.text)
    audioUrl.value = URL.createObjectURL(blob)

    if (props.autoPlay) {
      setTimeout(() => {
        if (audioRef.value) {
          audioRef.value.play().catch(() => {})
          playing.value = true
        }
      }, 100)
    }
  } catch (e) {
    console.warn('TTS 加载失败', e)
  } finally {
    loading.value = false
  }
}

function togglePlay() {
  if (!audioRef.value) return

  if (playing.value) {
    audioRef.value.pause()
    playing.value = false
  } else {
    audioRef.value.play().catch(() => {})
    playing.value = true
  }
}

function onEnded() {
  playing.value = false
  currentTime.value = 0
}

function onTimeUpdate() {
  if (audioRef.value) {
    currentTime.value = audioRef.value.currentTime
  }
}

function onLoaded() {
  if (audioRef.value) {
    audioDuration.value = audioRef.value.duration
  }
}

function onError() {
  loading.value = false
  playing.value = false
}

function seekAudio(event) {
  if (!audioRef.value || !progressRef.value) return
  const rect = progressRef.value.getBoundingClientRect()
  const x = event.clientX - rect.left
  const percent = x / rect.width
  audioRef.value.currentTime = percent * audioDuration.value
}

const progressPercent = computed(() => {
  if (!audioDuration.value) return 0
  return (currentTime.value / audioDuration.value) * 100
})

function formatAudioTime(seconds) {
  if (!seconds || isNaN(seconds)) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

onBeforeUnmount(() => {
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
  }
})
</script>

<style scoped>
.tts-player {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 8px;
}

.tts-player-idle {
  background: transparent;
  padding: 6px 4px;
}

.tts-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: #2563EB;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.2s;
}

.tts-btn:hover:not(:disabled) {
  background: #1D4ED8;
}

.tts-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tts-load-btn {
  background: transparent;
  color: #71717A;
  border: 1px solid #E4E4E7;
}

.tts-load-btn:hover:not(:disabled) {
  color: #2563EB;
  border-color: #2563EB;
  background: transparent;
}

.tts-progress {
  flex: 1;
  height: 4px;
  background: #D4D4D8;
  border-radius: 2px;
  cursor: pointer;
  overflow: hidden;
}

.tts-progress-fill {
  height: 100%;
  background: #2563EB;
  border-radius: 2px;
  transition: width 0.3s;
}

.tts-time {
  font-size: 11px;
  color: #71717A;
  white-space: nowrap;
  min-width: 60px;
  text-align: right;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin-icon {
  animation: spin 1s linear infinite;
}
</style>
