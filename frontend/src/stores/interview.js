import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useInterviewStore = defineStore('interview', () => {
  const isInInterview = ref(false)

  function setInterviewState(state) {
    isInInterview.value = state
  }

  function resetInterviewState() {
    isInInterview.value = false
  }

  return { isInInterview, setInterviewState, resetInterviewState }
})
