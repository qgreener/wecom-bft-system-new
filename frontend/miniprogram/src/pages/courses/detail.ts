import { fetchCourseDetail } from "../../services/app-api";
import type { CourseDetail, CourseSpec, LessonNode } from "../../types/api";
import { formatDateTime, formatYuan, statusLabel, statusTone } from "../../utils/format";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { go, optionNumber } from "../../utils/page";

type SpecView = CourseSpec & {
  price_text: string;
  status_text: string;
  selected: boolean;
};

type LessonView = LessonNode & {
  time_text: string;
  type_text: string;
};

function enabledLowest(specs: CourseSpec[]): CourseSpec | null {
  const enabled = specs.filter((item) => item.status === "ENABLED");
  return enabled.sort((left, right) => left.sale_price_cent - right.sale_price_cent)[0] ?? specs[0] ?? null;
}

Page({
  data: {
    loading: false,
    error: "",
    detail: null,
    specs: [] as SpecView[],
    lessons: [] as LessonView[],
    selectedSpecId: 0,
    selectedPriceText: "-"
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    const courseId = optionNumber(options, "course_id");
    void this.load(courseId);
  },

  async load(this: MiniPageThis, courseId: number) {
    this.setData({ loading: true, error: "" });
    try {
      const detail = await fetchCourseDetail(courseId);
      const selected = enabledLowest(detail.specs);
      const selectedSpecId = selected?.spec_id ?? 0;
      this.setData({
        loading: false,
        detail: {
          ...detail,
          type_text: statusLabel(detail.course_type),
          status_text: statusLabel(detail.status),
          status_tone: statusTone(detail.status)
        },
        specs: detail.specs.map((spec) => ({
          ...spec,
          price_text: formatYuan(spec.sale_price_cent),
          status_text: statusLabel(spec.status),
          selected: spec.spec_id === selectedSpecId
        })),
        lessons: detail.lesson_summary.map((node) => ({
          ...node,
          time_text: node.live_start_at ? formatDateTime(node.live_start_at) : "",
          type_text: statusLabel(node.lesson_type)
        })),
        selectedSpecId,
        selectedPriceText: formatYuan(selected?.sale_price_cent)
      });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "课程详情加载失败" });
    }
  },

  selectSpec(this: MiniPageThis, event: { currentTarget: { dataset: { id: number; price: number } } }) {
    const selectedSpecId = Number(event.currentTarget.dataset.id);
    const price = Number(event.currentTarget.dataset.price);
    const current = (this.data?.specs as SpecView[] | undefined) ?? [];
    this.setData({
      selectedSpecId,
      selectedPriceText: formatYuan(price),
      specs: current.map((spec) => ({ ...spec, selected: spec.spec_id === selectedSpecId }))
    });
  },

  buy(this: MiniPageThis) {
    const detail = this.data?.detail as CourseDetail | null;
    const selectedSpecId = Number(this.data?.selectedSpecId ?? 0);
    if (!detail || !selectedSpecId) {
      return;
    }
    go(`/pages/order/confirm?course_id=${detail.course_id}&spec_id=${selectedSpecId}`);
  }
});
