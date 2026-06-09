<template>
  <div class="app-wrapper" :class="{ 'interview-mode': isInterviewSession }">
    <Sidebar v-if="!isInterviewSession" />
    <div class="main-container">
      <Header v-if="!isInterviewSession" />
      <AppMain />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import AppMain from './components/AppMain.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const isInterviewSession = computed(() => {
  return route.name === 'InterviewSession'
})

onMounted(() => {
  if (userStore.token && !userStore.userInfo) {
    userStore.fetchUserInfo()
  }
})
</script>

<style lang="scss" scoped>
.app-wrapper {
  display: flex;
  height: 100vh;
  overflow: hidden;

  &.interview-mode {
    .main-container {
      width: 100%;
    }
  }
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: $bg-color;
}
</style>
