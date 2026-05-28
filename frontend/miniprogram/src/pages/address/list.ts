import { deleteAddress, fetchAddresses } from "../../services/app-api";
import type { AppAddress } from "../../types/api";
import type { MiniPageThis } from "../../utils/page";
import { go, toast } from "../../utils/page";

Page({
  data: {
    loading: false,
    items: [] as AppAddress[],
    error: ""
  },

  onShow(this: MiniPageThis) {
    this.loadList();
  },

  async loadList(this: MiniPageThis) {
    this.setData({ loading: true, error: "" });
    try {
      const list = await fetchAddresses();
      this.setData({ items: list, loading: false });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "加载失败" });
    }
  },

  add() {
    go("/pages/address/edit");
  },

  edit(this: MiniPageThis, e: { currentTarget: { dataset: { id: number } } }) {
    go(`/pages/address/edit?id=${e.currentTarget.dataset.id}`);
  },

  async remove(this: MiniPageThis, e: { currentTarget: { dataset: { id: number } } }) {
    const id = e.currentTarget.dataset.id;
    try {
      await deleteAddress(id);
      toast("已删除");
      await this.loadList();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  }
});
