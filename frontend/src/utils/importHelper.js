import * as XLSX from 'xlsx'

// 分类映射
const categoryMap = {
  java_basic: 'Java基础',
  spring: 'Spring',
  database: '数据库',
  frontend: '前端',
  algorithm: '算法',
  design_pattern: '设计模式',
  redis: 'Redis',
  devops: 'DevOps',
  microservice: '微服务',
  network: '网络',
  operating_system: '操作系统',
  project: '项目经验',
  architecture: '系统架构'
}

// 难度映射
const difficultyMap = {
  easy: '简单',
  medium: '中等',
  hard: '困难'
}

// 方向映射
const directionMap = {
  java_backend: 'Java后端',
  frontend: '前端',
  fullstack: '全栈',
  algorithm: '算法'
}

// 模板数据
const templateData = [
  {
    title: '示例题目：Java 中 == 和 equals 的区别',
    content: '请简述 Java 中 == 和 equals 的区别',
    answer: '== 比较引用地址，equals 默认比较地址，但可重写为比较内容',
    category: 'java_basic',
    difficulty: 'medium',
    direction: 'java_backend'
  }
]

/**
 * 解析 JSON 文件
 * @param {File} file - 文件对象
 * @returns {Promise<Array>} 解析后的数据数组
 */
export function parseJsonFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = JSON.parse(e.target.result)
        resolve(Array.isArray(data) ? data : [data])
      } catch (err) {
        reject(new Error('JSON 解析失败：文件格式不正确'))
      }
    }
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsText(file)
  })
}

/**
 * 解析 Excel 文件
 * @param {File} file - 文件对象
 * @returns {Promise<Array>} 解析后的数据数组
 */
export function parseExcelFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const workbook = XLSX.read(e.target.result, { type: 'array' })
        const sheetName = workbook.SheetNames[0]
        const data = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName])
        resolve(data)
      } catch (err) {
        reject(new Error('Excel 解析失败：文件格式不正确'))
      }
    }
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsArrayBuffer(file)
  })
}

/**
 * 解析 CSV 文件
 * @param {File} file - 文件对象
 * @returns {Promise<Array>} 解析后的数据数组
 */
export function parseCsvFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const text = e.target.result
        const workbook = XLSX.read(text, { type: 'string' })
        const sheetName = workbook.SheetNames[0]
        const data = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName])
        resolve(data)
      } catch (err) {
        reject(new Error('CSV 解析失败：文件格式不正确'))
      }
    }
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsText(file)
  })
}

/**
 * 统一入口函数，根据文件扩展名调用对应解析函数
 * @param {File} file - 文件对象
 * @returns {Promise<Array>} 解析后的数据数组
 */
export async function parseImportFile(file) {
  const fileName = file.name.toLowerCase()
  
  if (fileName.endsWith('.json')) {
    return parseJsonFile(file)
  } else if (fileName.endsWith('.xlsx') || fileName.endsWith('.xls')) {
    return parseExcelFile(file)
  } else if (fileName.endsWith('.csv')) {
    return parseCsvFile(file)
  } else {
    throw new Error('不支持的文件格式，请上传 JSON、Excel 或 CSV 文件')
  }
}

/**
 * 下载 JSON 格式的导入模板
 */
export function downloadTemplate() {
  const jsonStr = JSON.stringify(templateData, null, 2)
  const blob = new Blob([jsonStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '题目导入模板.json'
  link.click()
  URL.revokeObjectURL(url)
}

/**
 * 校验单条数据
 * @param {Object} item - 待校验的数据对象
 * @returns {Object} { valid: boolean, errors: string[] }
 */
export function validateQuestion(item) {
  const errors = []
  
  if (!item.title || typeof item.title !== 'string' || !item.title.trim()) {
    errors.push('title 为必填项，且必须为非空字符串')
  }
  
  if (!item.content || typeof item.content !== 'string' || !item.content.trim()) {
    errors.push('content 为必填项，且必须为非空字符串')
  }
  
  if (!item.answer || typeof item.answer !== 'string' || !item.answer.trim()) {
    errors.push('answer 为必填项，且必须为非空字符串')
  }
  
  if (!item.category || !Object.keys(categoryMap).includes(item.category)) {
    errors.push(`category 为必填项，可选值：${Object.keys(categoryMap).join(', ')}`)
  }
  
  if (!item.difficulty || !Object.keys(difficultyMap).includes(item.difficulty)) {
    errors.push(`difficulty 为必填项，可选值：${Object.keys(difficultyMap).join(', ')}`)
  }
  
  if (!item.direction || !Object.keys(directionMap).includes(item.direction)) {
    errors.push(`direction 为必填项，可选值：${Object.keys(directionMap).join(', ')}`)
  }
  
  return { valid: errors.length === 0, errors }
}

/**
 * 获取校验错误信息字符串
 * @param {Object} item - 待校验的数据对象
 * @returns {string} 错误信息，无错误返回空字符串
 */
export function getValidationError(item) {
  const { errors } = validateQuestion(item)
  return errors.join('; ')
}

/**
 * 批量校验，返回带校验结果的数据
 * @param {Array} data - 待校验的数据数组
 * @returns {Array} 带 valid 和 errors 字段的数据数组
 */
export function validateQuestions(data) {
  return data.map((item, index) => {
    const { valid, errors } = validateQuestion(item)
    return {
      ...item,
      _index: index + 1,
      _valid: valid,
      _errors: errors
    }
  })
}

export { categoryMap, difficultyMap, directionMap }
