Page({
  goCourses() {
    wx.switchTab({ url: "/pages/courses/list" });
  },
  goLearning() {
    wx.switchTab({ url: "/pages/learning/index" });
  },
  goMine() {
    wx.switchTab({ url: "/pages/mine/index" });
  },
  goOrders() {
    wx.navigateTo({ url: "/pages/order/list" });
  },
  goRefund() {
    wx.navigateTo({ url: "/pages/refund/apply" });
  },
  goInvoice() {
    wx.navigateTo({ url: "/pages/invoice/titles" });
  }
});
