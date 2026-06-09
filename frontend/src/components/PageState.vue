<template>
  <div v-if="loading" class="loading-state">
    <div class="loading-spinner"></div>
    <span>加载中...</span>
  </div>
  <div v-else-if="error" class="error-state">
    <span>{{ error }}</span>
    <el-button @click="$emit('retry')">重试</el-button>
  </div>
  <div v-else-if="empty" class="empty-state">
    <slot name="empty">
      <p>暂无数据</p>
    </slot>
  </div>
  <slot v-else />
</template>

<script setup>
defineProps({
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  empty: {
    type: Boolean,
    default: false
  }
})

defineEmits(['retry'])
</script>
