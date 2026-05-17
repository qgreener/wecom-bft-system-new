import { fetchOrderDetail } from "../../services/app-api";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { optionNumber } from "../../utils/page";
import { receiverLine } from "../../utils/snapshot";
import { formatDateTime, statusLabel, statusTone } from "../../utils/format";
import type { Shipment } from "../../types/api";

function shipmentView(item: Shipment) {
  return {
    ...item,
    status_text: statusLabel(item.status),
    status_tone: statusTone(item.status),
    receiver_line: receiverLine(item.receiver_snapshot),
    created_text: formatDateTime(item.created_at)
  };
}

Page({
  data: {
    loading: false,
    error: "",
    order: null,
    shipments: [] as ReturnType<typeof shipmentView>[],
    gap: "当前后端未提供独立的 /api/app/orders/{id}/logistics 轨迹接口，页面仅展示订单详情中的发货单摘要。"
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    void this.load(optionNumber(options, "order_id"));
  },

  async load(this: MiniPageThis, orderId: number) {
    this.setData({ loading: true, error: "" });
    try {
      const order = await fetchOrderDetail(orderId);
      this.setData({ loading: false, order, shipments: (order.shipments ?? []).map(shipmentView) });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "物流详情加载失败" });
    }
  }
});
