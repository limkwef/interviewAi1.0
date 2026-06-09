<template>
  <div class="common-table">
    <div class="search-bar">
      <el-input
        v-model="keyword"
        :placeholder="searchPlaceholder"
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      />
      <div class="search-actions">
        <slot name="actions" />
        <el-button v-if="showAdd" type="primary" @click="$emit('add')">新增</el-button>
      </div>
    </div>
    <el-table :data="data" v-loading="loading" v-bind="$attrs">
      <slot />
    </el-table>
    <div v-if="total > 0" class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPageModel"
        v-model:page-size="pageSizeModel"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="$emit('pageChange', $event)"
        @size-change="$emit('sizeChange', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  total: {
    type: Number,
    default: 0
  },
  loading: {
    type: Boolean,
    default: false
  },
  pageSize: {
    type: Number,
    default: 10
  },
  currentPage: {
    type: Number,
    default: 1
  },
  searchPlaceholder: {
    type: String,
    default: '搜索...'
  },
  showAdd: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['search', 'add', 'pageChange', 'sizeChange'])

const keyword = ref('')
const currentPageModel = ref(props.currentPage)
const pageSizeModel = ref(props.pageSize)

watch(() => props.currentPage, (val) => {
  currentPageModel.value = val
})

watch(() => props.pageSize, (val) => {
  pageSizeModel.value = val
})

function handleSearch() {
  emit('search', keyword.value)
}
</script>

<style scoped>
.common-table {
  padding: 20px;
}
.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}
.search-bar .el-input {
  max-width: 320px;
}
.search-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
