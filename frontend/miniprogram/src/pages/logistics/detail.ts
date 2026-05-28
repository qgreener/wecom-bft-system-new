import { fetchOrderDetail, fetchOrderLogistics } from "../../services/app-api";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { optionNumber } from "../../utils/page";
import { receiverLine } from "../../utils/snapshot";
import { formatDateTime, statusLabel, statusTone } from "../../utils/format";
import type { LogisticsTrace, Shipment, ShipmentDetail } from "../../types/api";

function shipmentView(item: Shipment, traces: LogisticsTrace[]) {
  const traceList = (traces ?? [])
    .slice()
    .sort((a, b) => String(a.logistics_node_time).localeCompare(String(b.logistics_node_time)))
    .map((t) => ({
      ...t,
      time_text: formatDateTime(t.logistics_node_time),
      node_status_text: statusLabel(t.node_status)
    }));
  return {
    ...item,
    status_text: statusLabel(item.status),
    status_tone: statusTone(item.status),
    receiver_line: receiverLine(item.receiver_snapshot),
    created_text: formatDateTime(item.created_at),
    shipped_text: formatDateTime(item.shipped_at),
    signed_text: formatDateTime(item.signed_at),
    traces: traceList
  };
}

Page({
  data: {
    loading: false,
    error: "",
    shipments: [] as ReturnType<typeof shipmentView>[]
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    void this.load(optionNumber(options, "order_id"));
  },

  async load(this: MiniPageThis, orderId: number) {
    this.setData({ loading: true, error: "" });
    try {
      // 同时拉订单（拿收货地址快照）+ 物流（拿轨迹）
      const [order, logistics] = await Promise.all([
        fetchOrderDetail(orderId),
        fetchOrderLogistics(orderId).catch(() => [] as ShipmentDetail[])
      ]);
      const baseShipments = order.shipments ?? [];
      // 用物流接口的 traces 补全到对应 shipment
      const traceMap = new Map<number, LogisticsTrace[]>();
      for (const detail of logistics) {
        traceMap.set(detail.shipment.shipment_id, detail.traces ?? []);
      }
      this.setData({
        loading: false,
        shipments: baseShipments.map((ship) =>
          shipmentView(ship, traceMap.get(ship.shipment_id) ?? [])
        )
      });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "物流详情加载失败" });
    }
  }
});
