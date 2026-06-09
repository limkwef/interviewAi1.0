import request from '@/utils/request'

/**
 * 上传音频进行语音识别（ASR）
 * @param {Blob} audioBlob  录音 Blob（PCM 格式）
 * @param {string} format   音频格式 (pcm/wav)
 * @returns {Promise<string>} 识别结果文字
 */
export function uploadAudio(audioBlob, format = 'pcm') {
  const formData = new FormData()
  formData.append('audio', audioBlob, `recording.${format}`)
  formData.append('format', format)

  return request({
    url: '/speech/asr',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data: formData
  }).then(res => {
    if (res.code === 200) return res.data.text
    throw new Error(res.message || '语音识别失败')
  })
}

/**
 * 获取 TTS 语音音频
 * @param {string} text  待合成文本
 * @param {Object} options  可选参数 { per, spd, pit, vol }
 * @returns {Promise<Blob>} MP3 音频 Blob
 */
export function fetchTts(text, options = {}) {
  return request({
    url: '/speech/tts',
    method: 'post',
    data: { text, ...options },
    responseType: 'blob'
  }).then(res => {
    return res
  })
}
