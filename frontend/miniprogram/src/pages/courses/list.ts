import { fetchCourses } from "../../services/app-api";
import type { AppCourse } from "../../types/api";
import { formatYuan, statusLabel, statusTone } from "../../utils/format";
import type { MiniPageThis } from "../../utils/page";
import { go } from "../../utils/page";

type CourseView = AppCourse & {
  price_text: string;
  type_text: string;
  status_text: string;
  status_tone: string;
};

function toView(record: AppCourse): CourseView {
  return {
    ...record,
    price_text: formatYuan(record.min_sale_price_cent),
    type_text: statusLabel(record.course_type),
    status_text: statusLabel(record.status),
    status_tone: statusTone(record.status)
  };
}

Page({
  data: {
    loading: false,
    error: "",
    keyword: "",
    courseType: "",
    records: [] as CourseView[]
  },

  onLoad(this: MiniPageThis) {
    void this.load();
  },

  onPullDownRefresh(this: MiniPageThis) {
    void this.load().finally(() => wx.stopPullDownRefresh());
  },

  onKeywordInput(this: MiniPageThis, event: { detail: { value: string } }) {
    this.setData({ keyword: event.detail.value });
  },

  setType(this: MiniPageThis, event: { currentTarget: { dataset: { type?: string } } }) {
    this.setData({ courseType: event.currentTarget.dataset.type ?? "" });
    void this.load();
  },

  search(this: MiniPageThis) {
    void this.load();
  },

  async load(this: MiniPageThis) {
    const data = this.data ?? {};
    this.setData({ loading: true, error: "" });
    try {
      const page = await fetchCourses({
        keyword: String(data.keyword ?? ""),
        course_type: String(data.courseType ?? ""),
        page_no: 1,
        page_size: 20
      });
      this.setData({ loading: false, records: page.records.map(toView) });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "课程加载失败" });
    }
  },

  openDetail(event: { currentTarget: { dataset: { id: number } } }) {
    go(`/pages/courses/detail?course_id=${event.currentTarget.dataset.id}`);
  }
});
