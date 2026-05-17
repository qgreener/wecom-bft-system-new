import { fetchLessons } from "../../services/app-api";
import { formatDateTime, statusLabel } from "../../utils/format";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { optionNumber } from "../../utils/page";

Page({
  data: {
    loading: false,
    error: "",
    detail: null,
    nodes: [] as Array<Record<string, unknown>>
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    void this.load(optionNumber(options, "entitlement_id"));
  },

  async load(this: MiniPageThis, entitlementId: number) {
    this.setData({ loading: true, error: "" });
    try {
      const detail = await fetchLessons(entitlementId);
      const nodes = detail.nodes.map((node) => {
        const item = node as Record<string, unknown>;
        return {
          ...item,
          type_text: statusLabel(String(item.lesson_type ?? item.node_type ?? "")),
          live_time_text: item.live_start_at ? formatDateTime(String(item.live_start_at)) : ""
        };
      });
      this.setData({ loading: false, detail, nodes });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "课程学习页加载失败" });
    }
  },

  previewQr(this: MiniPageThis) {
    const detail = this.data?.detail as { course_group_qr?: string } | null;
    if (detail?.course_group_qr) {
      wx.previewImage({ urls: [detail.course_group_qr] });
    }
  }
});
