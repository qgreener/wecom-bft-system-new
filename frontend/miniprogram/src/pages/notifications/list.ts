import { fetchNotifications, markNotificationRead } from "../../services/app-api";
import type { NotificationItem } from "../../types/api";
import type { MiniPageThis } from "../../utils/page";
import { toast } from "../../utils/page";

Page({
  data: {
    loading: false,
    items: [] as NotificationItem[],
    error: ""
  },

  onShow(this: MiniPageThis) {
    this.loadList();
  },

  async loadList(this: MiniPageThis) {
    this.setData({ loading: true, error: "" });
    try {
      const list = await fetchNotifications();
      this.setData({ items: list, loading: false });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "加载失败" });
    }
  },

  async onItemTap(this: MiniPageThis, e: { currentTarget: { dataset: { id: number; index: number } } }) {
    const { id, index } = e.currentTarget.dataset;
    if (!id) return;
    try {
      const item = this.data.items[index];
      if (item && item.read_status !== "READ") {
        const updated = await markNotificationRead(id);
        const next = [...this.data.items];
        next[index] = updated;
        this.setData({ items: next });
      }
    } catch (error) {
      toast(error instanceof Error ? error.message : "标记失败");
    }
  }
});
