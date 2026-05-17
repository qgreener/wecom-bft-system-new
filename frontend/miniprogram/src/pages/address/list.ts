import type { MiniPageThis } from "../../utils/page";
import { go } from "../../utils/page";

Page({
  data: {
    loading: false,
    error: "当前后端未提供 /api/app/addresses 接口，地址管理页面仅保留入口，不展示伪造数据。"
  },

  add() {
    go("/pages/address/edit");
  }
});
