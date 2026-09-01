import { request } from "../../utils/api";

interface LoginResponse {
  accessToken: string;
  expiresAt: string;
  user: { userId: string; role: "ADMIN" };
}

interface LoginPageData {
  loginCode: string;
  enrollmentSecret: string;
  submitting: boolean;
  errorMessage: string;
}

interface LoginPageMethods {
  onCodeInput(event: WechatMiniprogram.Input): void;
  onSecretInput(event: WechatMiniprogram.Input): void;
  submitLocalLogin(): Promise<void>;
}

Page<LoginPageData, LoginPageMethods>({
  data: {
    loginCode: "",
    enrollmentSecret: "",
    submitting: false,
    errorMessage: ""
  },

  onCodeInput(event: WechatMiniprogram.Input) {
    this.setData({loginCode: event.detail.value});
  },

  onSecretInput(event: WechatMiniprogram.Input) {
    this.setData({enrollmentSecret: event.detail.value});
  },

  async submitLocalLogin() {
    if (!this.data.loginCode) {
      this.setData({errorMessage: "请输入本地登录 code"});
      return;
    }
    this.setData({submitting: true, errorMessage: ""});
    try {
      const response = await request<LoginResponse>("/api/v1/auth/wechat/login", "POST", {
        code: this.data.loginCode,
        ...(this.data.enrollmentSecret ? {bootstrapEnrollmentSecret: this.data.enrollmentSecret} : {})
      });
      wx.setStorageSync("investment.accessToken", response.accessToken);
      wx.setStorageSync("investment.sessionExpiresAt", response.expiresAt);
      wx.switchTab({url: "/pages/overview/overview"});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "登录失败，请检查本地服务与配置"});
    } finally {
      this.setData({submitting: false});
    }
  }
});
