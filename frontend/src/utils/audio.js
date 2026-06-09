/**
 * 录音管理器
 *
 * 使用 MediaRecorder API 录制用户语音
 * 输出格式: audio/webm (Chrome) / audio/wav (可降级)
 *
 * 使用示例:
 *   const recorder = new AudioRecorder()
 *   await recorder.start()
 *   // ... 用户说话 ...
 *   const blob = await recorder.stop()
 *   const pcmBlob = await convertToPcm(blob)
 *   const text = await uploadAudio(pcmBlob)
 */
export class AudioRecorder {
  constructor(options = {}) {
    this.mediaRecorder = null
    this.audioChunks = []
    this.stream = null
    this.startTime = 0
    this.onVolumeChange = options.onVolumeChange || null
    this.audioContext = null
    this.analyserNode = null
    this.mimeType = this._getSupportedMimeType()
  }

  /**
   * 获取浏览器支持的音频格式
   * 优先顺序: audio/webm;codecs=pcm > audio/webm > audio/wav
   */
  _getSupportedMimeType() {
    const types = [
      'audio/webm;codecs=pcm',
      'audio/webm',
      'audio/wav',
      'audio/ogg;codecs=opus'
    ]
    for (const type of types) {
      if (MediaRecorder.isTypeSupported(type)) return type
    }
    return ''
  }

  /**
   * 启动录音
   * 请求麦克风权限 → 创建 MediaRecorder → 开始录制
   */
  async start() {
    this.audioChunks = []
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        sampleRate: 16000,
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true
      }
    })

    // 创建 AnalyserNode 用于音量可视化
    if (this.onVolumeChange) {
      this.audioContext = new AudioContext()
      const source = this.audioContext.createMediaStreamSource(this.stream)
      this.analyserNode = this.audioContext.createAnalyser()
      this.analyserNode.fftSize = 256
      source.connect(this.analyserNode)
      this._startVolumeMeter()
    }

    return new Promise((resolve, reject) => {
      if (!this.mimeType) {
        reject(new Error('浏览器不支持音频录制'))
        return
      }

      this.mediaRecorder = new MediaRecorder(this.stream, {
        mimeType: this.mimeType
      })

      this.mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          this.audioChunks.push(event.data)
        }
      }

      this.mediaRecorder.onerror = () => {
        reject(new Error('录音出错'))
      }

      this.mediaRecorder.start()
      this.startTime = Date.now()
      resolve()
    })
  }

  /**
   * 停止录音
   * @returns {Promise<Blob>} 音频 Blob
   */
  stop() {
    return new Promise((resolve) => {
      if (!this.mediaRecorder || this.mediaRecorder.state === 'inactive') {
        resolve(new Blob([], { type: this.mimeType }))
        return
      }

      this.mediaRecorder.onstop = () => {
        const blob = new Blob(this.audioChunks, { type: this.mimeType })
        this._cleanup()
        resolve(blob)
      }

      this.mediaRecorder.stop()
      if (this.stream) {
        this.stream.getTracks().forEach(track => track.stop())
      }
    })
  }

  /**
   * 获取录音时长（秒）
   */
  getDuration() {
    if (!this.startTime) return 0
    return (Date.now() - this.startTime) / 1000
  }

  /**
   * 启动音量检测（用于可视化）
   */
  _startVolumeMeter() {
    const dataArray = new Uint8Array(this.analyserNode.frequencyBinCount)
    const tick = () => {
      if (!this.analyserNode) return
      this.analyserNode.getByteFrequencyData(dataArray)
      const average = dataArray.reduce((a, b) => a + b) / dataArray.length
      if (this.onVolumeChange) {
        this.onVolumeChange(average / 255)
      }
      requestAnimationFrame(tick)
    }
    tick()
  }

  /**
   * 清理资源
   */
  _cleanup() {
    if (this.audioContext) {
      this.audioContext.close()
      this.audioContext = null
    }
    this.analyserNode = null
    this.stream = null
    this.mediaRecorder = null
  }
}

/**
 * 将录音 Blob 转换为适合百度 ASR 的 PCM 格式
 * 百度 ASR 要求: 16kHz, 单声道, 16bit PCM
 *
 * @param {Blob} audioBlob  原始录音
 * @returns {Promise<Blob>} 转换后的 PCM Blob
 */
export async function convertToPcm(audioBlob) {
  console.log('[Audio] 开始转换 PCM，原始大小:', audioBlob.size, 'bytes，类型:', audioBlob.type)
  
  // 只有纯 PCM 格式（audio/pcm）才直接返回
  // 注意：audio/webm;codecs=pcm 是 WebM 容器，不是裸 PCM！
  if (audioBlob.type === 'audio/pcm') {
    console.log('[Audio] 已是裸 PCM 格式，直接返回')
    return audioBlob
  }
  
  console.log('[Audio] 需要解码:', audioBlob.type, '→ audio/pcm')

  // 解码原始音频（不指定采样率，保持原始格式）
  const arrayBuffer = await audioBlob.arrayBuffer()
  const audioContext = new AudioContext()
  const audioBuffer = await audioContext.decodeAudioData(arrayBuffer)
  
  console.log('[Audio] 解码完成，采样率:', audioBuffer.sampleRate, 'Hz，声道数:', audioBuffer.numberOfChannels, '，时长:', audioBuffer.duration.toFixed(2), '秒')

  // 如果采样率已经是 16000，直接提取数据
  if (audioBuffer.sampleRate === 16000) {
    console.log('[Audio] 采样率已是 16000Hz，直接提取 PCM')
    const pcmData = audioBufferToPcm(audioBuffer)
    audioContext.close()
    console.log('[Audio] PCM 转换完成，大小:', pcmData.length * 2, 'bytes')
    return new Blob([pcmData.buffer], { type: 'audio/pcm' })
  }

  // 需要重采样到 16000Hz
  console.log('[Audio] 需要重采样:', audioBuffer.sampleRate, '→ 16000')
  const resampledBuffer = await resampleAudio(audioBuffer, 16000, audioContext)
  console.log('[Audio] 重采样完成，新采样率:', resampledBuffer.sampleRate, '，新时长:', resampledBuffer.duration.toFixed(2), '秒')
  
  const pcmData = audioBufferToPcm(resampledBuffer)
  audioContext.close()
  
  console.log('[Audio] PCM 转换完成，PCM 数据长度:', pcmData.length, '，字节数:', pcmData.length * 2)
  return new Blob([pcmData.buffer], { type: 'audio/pcm' })
}

/**
 * 将 AudioBuffer 转换为 16bit PCM 数据
 */
function audioBufferToPcm(audioBuffer) {
  // 合并多声道为单声道
  const channels = audioBuffer.numberOfChannels
  const length = audioBuffer.length
  const pcmData = new Int16Array(length)
  
  if (channels === 1) {
    const channelData = audioBuffer.getChannelData(0)
    for (let i = 0; i < length; i++) {
      const s = Math.max(-1, Math.min(1, channelData[i]))
      pcmData[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
    }
  } else {
    // 多声道混合
    for (let i = 0; i < length; i++) {
      let sum = 0
      for (let c = 0; c < channels; c++) {
        sum += audioBuffer.getChannelData(c)[i]
      }
      const s = Math.max(-1, Math.min(1, sum / channels))
      pcmData[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
    }
  }
  
  return pcmData
}

/**
 * 音频重采样
 * @param {AudioBuffer} audioBuffer 原始音频
 * @param {number} targetSampleRate 目标采样率
 * @param {AudioContext} audioContext AudioContext 实例
 */
async function resampleAudio(audioBuffer, targetSampleRate, audioContext) {
  const sourceSampleRate = audioBuffer.sampleRate
  
  // 创建离线音频上下文进行重采样
  const offlineContext = new OfflineAudioContext(
    1,  // 目标声道数
    Math.ceil(audioBuffer.length * targetSampleRate / sourceSampleRate),
    targetSampleRate
  )
  
  // 创建音频源
  const bufferSource = offlineContext.createBufferSource()
  bufferSource.buffer = audioBuffer
  
  // 连接到目标
  bufferSource.connect(offlineContext.destination)
  
  // 开始渲染
  bufferSource.start()
  const resampledBuffer = await offlineContext.startRendering()
  
  return resampledBuffer
}
