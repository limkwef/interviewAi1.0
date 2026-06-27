/**
 * 面试系统常量映射表
 *
 * 集中管理所有枚举映射，各组件统一从此引入，
 * 避免重复定义和维护不一致。
 */

/** 岗位方向映射 */
export const positionMap = {
  java_backend: 'Java后端',
  frontend: '前端开发',
  fullstack: '全栈开发',
  algorithm: '算法工程师',
  hr: 'HR/软素质'
}

/** 旧版中文岗位名 → 英文 code（兼容历史数据） */
export const legacyPositionMap = {
  'Java后端开发': 'java_backend',
  'Java后端': 'java_backend',
  '前端开发': 'frontend',
  '全栈开发': 'fullstack',
  '算法工程师': 'algorithm',
  '数据分析': 'algorithm',
  '产品经理': 'frontend'
}

/**
 * 将岗位值统一为英文 code
 * 如果传入的是英文 code，直接返回；如果是旧中文值，转换后返回
 */
export function normalizePosition(value) {
  if (!value) return ''
  if (positionMap[value]) return value  // 已经是英文 code
  return legacyPositionMap[value] || value
}

/** 面试轮次映射 */
export const roundMap = {
  technical: '技术面',
  hr: 'HR面',
  comprehensive: '综合面'
}

/** 难度等级映射 */
export const difficultyMap = {
  easy: '简单',
  medium: '中等',
  hard: '困难'
}

/** 题目分类映射 */
export const categoryMap = {
  java_basic: 'Java基础',
  spring: 'Spring框架',
  database: '数据库',
  redis: 'Redis',
  design_pattern: '设计模式',
  algorithm: '算法',
  frontend: '前端',
  devops: '运维部署',
  microservice: '微服务',
  network: '网络',
  operating_system: '操作系统',
  project: '项目经验',
  architecture: '系统架构',
  behavioral: '行为面试'
}

/** 面试官人格映射 */
export const personaMap = {
  mentor: '温和导师型',
  stress: '压力面试型',
  deep_dive: '技术深究型',
  behavioral: '行为面试型'
}

/** 获取映射值的工具函数（带降级，找不到 key 时返回 key 本身） */
export function mapValue(map, key, fallback) {
  return map[key] || fallback || key || ''
}
