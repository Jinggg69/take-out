// Shared by the detail and legacy history reminder entries.
export default {
  data() { return { reminderCooldowns: {}, reminderPending: {}, reminderTimer: null, reminderActive: true, reminderRun: 0 }; },
  onShow() { this.reminderActive = true; this.tickReminder(); },
  onHide() { this.stopReminder(); },
  onUnload() { this.stopReminder(); },
  methods: {
    reminderRemaining(id) { return this.reminderCooldowns[id]?.seconds || 0; },
    stopReminder() { this.reminderActive = false; this.reminderRun++; clearTimeout(this.reminderTimer); this.reminderTimer = null; this.reminderPending = {}; },
    tickReminder() {
      clearTimeout(this.reminderTimer);
      const now = Date.now(), next = {};
      for (const [id, value] of Object.entries(this.reminderCooldowns)) {
        const seconds = Math.max(0, Math.ceil((value.until - now) / 1000));
        if (seconds) next[id] = { ...value, seconds };
      }
      this.reminderCooldowns = next;
      if (this.reminderActive && Object.keys(next).length) this.reminderTimer = setTimeout(() => this.tickReminder(), 1000);
    },
    async runReminder(id, action) {
      if (!this.reminderActive || this.reminderRemaining(id) || this.reminderPending[id]) return;
      this.reminderPending = { ...this.reminderPending, [id]: true };
      const run = this.reminderRun;
      try { await action(); }
      catch (error) {
        if (!this.reminderActive || run !== this.reminderRun || error.staleIdentity || error.staleStore) return;
        if (error.statusCode === 429 || (error.statusCode === 503 && error.retryAfterSeconds != null)) {
          const raw = Number(error.retryAfterSeconds), seconds = Number.isFinite(raw) && raw > 0 ? Math.ceil(raw) : 3;
          this.reminderCooldowns = { ...this.reminderCooldowns, [id]: { seconds, until: Date.now() + seconds * 1000 } };
          this.tickReminder();
        }
        uni.showToast({ title: error.msg || '催单暂不可用，请稍后重试', icon: 'none' });
      } finally { if (run === this.reminderRun) this.reminderPending = { ...this.reminderPending, [id]: false }; }
    },
  },
};
