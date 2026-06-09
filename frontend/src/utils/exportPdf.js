import html2pdf from 'html2pdf.js'

/**
 * 检查浏览器是否支持 showSaveFilePicker API
 */
function supportsFileSystemAccess() {
  return 'showSaveFilePicker' in window
}

/**
 * 让用户选择保存位置并保存 PDF
 * @param {HTMLElement} element - 要导出的 DOM 元素
 * @param {string} filename - 默认文件名
 * @param {Object} options - 配置选项
 */
export async function exportToPdf(element, filename = 'export.pdf', options = {}) {
  if (!element) {
    throw new Error('导出元素不存在')
  }

  const defaultOptions = {
    margin: [10, 10, 10, 10],
    image: { type: 'jpeg', quality: 0.98 },
    html2canvas: {
      scale: 2,
      useCORS: true,
      logging: false,
      letterRendering: true
    },
    jsPDF: {
      unit: 'mm',
      format: 'a4',
      orientation: 'portrait'
    },
    pagebreak: { mode: ['avoid-all', 'css', 'legacy'] }
  }

  const mergedOptions = { ...defaultOptions, ...options }

  // 如果浏览器支持 File System Access API，让用户选择保存位置
  if (supportsFileSystemAccess()) {
    try {
      const handle = await window.showSaveFilePicker({
        suggestedName: filename,
        types: [
          {
            description: 'PDF 文件',
            accept: { 'application/pdf': ['.pdf'] }
          }
        ]
      })

      // 生成 PDF blob
      const blob = await html2pdf().set(mergedOptions).from(element).outputPdf('blob')

      // 写入用户选择的位置
      const writable = await handle.createWritable()
      await writable.write(blob)
      await writable.close()

      return true
    } catch (err) {
      // 用户取消选择
      if (err.name === 'AbortError') {
        return false
      }
      throw err
    }
  } else {
    // 不支持的浏览器，使用默认下载
    mergedOptions.filename = filename
    await html2pdf().set(mergedOptions).from(element).save()
    return true
  }
}

/**
 * 导出面试报告为 PDF
 * @param {string} reportId - 报告 ID
 */
export async function exportReportPdf(reportId) {
  const element = document.querySelector('.report-detail-page') || document.querySelector('.report-container')
  if (!element) {
    throw new Error('未找到报告内容')
  }

  const filename = `面试报告_${reportId}_${formatDate(new Date())}.pdf`
  return exportToPdf(element, filename)
}

/**
 * 导出诊断报告为 PDF
 * @param {string} diagnosisId - 诊断报告 ID
 */
export async function exportDiagnosisPdf(diagnosisId) {
  const element = document.querySelector('.diagnosis-page')
  if (!element) {
    throw new Error('未找到诊断报告内容')
  }

  const filename = `诊断报告_${diagnosisId}_${formatDate(new Date())}.pdf`
  return exportToPdf(element, filename)
}

/**
 * 导出学习计划为 PDF
 */
export async function exportLearningPlanPdf() {
  const element = document.querySelector('.learning-path-page') || document.querySelector('.learning-plan')
  if (!element) {
    throw new Error('未找到学习计划内容')
  }

  const filename = `学习计划_${formatDate(new Date())}.pdf`
  return exportToPdf(element, filename)
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}${month}${day}`
}
