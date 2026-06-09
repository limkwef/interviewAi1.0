<template>
  <router-view />
</template>

<script setup>
import { onErrorCaptured, ref } from 'vue'

const hasError = ref(false)

onErrorCaptured((err, instance, info) => {
  console.error('全局错误捕获:', err, info)
  hasError.value = true
  return false
})
</script>

<style lang="scss">
</style>
