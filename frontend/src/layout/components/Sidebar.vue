<template>
  <div class="sidebar">
    <div class="logo">
      <img src="@/assets/logo.svg" alt="Logo" class="logo-icon" />
      <span v-show="!isCollapsed" class="logo-title">AI面试系统</span>
    </div>
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <template v-for="item in menuRoutes" :key="item.path">
          <el-sub-menu v-if="item.children && item.children.length > 1" :index="item.path">
            <template #title>
              <el-icon><component :is="item.meta?.icon" /></el-icon>
              <span>{{ item.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children.filter(c => !c.meta?.hidden)"
              :key="child.path"
              :index="`/${item.path}/${child.path}`"
              @click="handleMenuClick(`/${item.path}/${child.path}`)"
            >
              {{ child.meta?.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="getMenuPath(item)" @click="handleMenuClick(getMenuPath(item))">
            <el-icon><component :is="item.meta?.icon || item.children?.[0]?.meta?.icon" /></el-icon>
            <template #title>{{ item.meta?.title || item.children?.[0]?.meta?.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapsed = ref(false)

const activeMenu = computed(() => route.path)

const menuRoutes = computed(() => {
  const mainRoute = router.options.routes.find(r => r.path === '/')
  if (!mainRoute || !mainRoute.children) return []
  return mainRoute.children.filter(r => {
    if (r.meta?.hidden) return false
    if (r.meta?.requiresAdmin && !userStore.isAdmin) return false
    return true
  })
})

function getMenuPath(item) {
  if (item.children && item.children.length === 1) {
    return `/${item.path}/${item.children[0].path}`
  }
  return `/${item.path}`
}

function handleMenuClick(path) {
  router.push(path)
}
</script>

<style lang="scss" scoped>
.sidebar {
  width: $sidebar-width;
  height: 100vh;
  background-color: $sidebar-bg;
  transition: width 0.3s;
  overflow: hidden;

  &:not(.collapse) {
    width: $sidebar-width;
  }

  &.collapse {
    width: $sidebar-collapsed-width;
  }
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  background-color: #2b2f3a;

  .logo-icon {
    width: 32px;
    height: 32px;
  }

  .logo-title {
    margin-left: 10px;
    color: #fff;
    font-size: 16px;
    font-weight: 600;
    white-space: nowrap;
  }
}

.el-menu {
  border-right: none;
}
</style>
