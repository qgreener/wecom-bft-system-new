import { fetchEntitlements } from "../../services/app-api";
import { getStoredSession } from "../../stores/session";
import type { Entitlement } from "../../types/api";
import { formatDateTime, statusLabel, statusTone } from "../../utils/format";
import { courseTitle, parseSnapshot } from "../../utils/snapshot";
import type { MiniPageThis } from "../../utils/page";
import { go } from "../../utils/page";

type EntitlementView = Entitlement & {
  course_title: string;
  status_text: string;
  status_tone: string;
  opened_text: string;
  can_learn: boolean;
};

function toView(item: Entitlement): EntitlementView {
  return {
    ...item,
    course_title: courseTitle(parseSnapshot(item.course_snapshot)),
    status_text: statusLabel(item.status),
    status_tone: statusTone(item.status),
    opened_text: formatDateTime(item.opened_at),
    can_learn: item.status === "ACTIVE"
  };
}

Page({
  data: {
    loading: false,
    error: "",
    records: [] as EntitlementView[],
    status: "ACTIVE",
    loggedIn: false
  },

  onShow(this: MiniPageThis) {
    const loggedIn = Boolean(getStoredSession());
    this.setData({ loggedIn });
    if (loggedIn) {
      void this.load();
    }
  },

  setStatus(this: MiniPageThis, event: { currentTarget: { dataset: { status?: string } } }) {
    this.setData({ status: event.currentTarget.dataset.status ?? "" });
    void this.load();
  },

  async load(this: MiniPageThis) {
    this.setData({ loading: true, error: "" });
    try {
      const page = await fetchEntitlements({
        status: String(this.data?.status ?? ""),
        page_no: 1,
        page_size: 20
      });
      this.setData({ loading: false, records: page.records.map(toView) });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "学习权益加载失败" });
    }
  },

  login() {
    go("/pages/auth/login");
  },

  openLearning(event: { currentTarget: { dataset: { id: number; active: boolean } } }) {
    if (!event.currentTarget.dataset.active) {
      return;
    }
    go(`/pages/learning/detail?entitlement_id=${event.currentTarget.dataset.id}`);
  }
});
