import { runtimeConfig } from "../config";

export interface ApiProblem {
  code: string;
  message: string;
  traceId: string;
  details: string[];
}

type RequestHeaders = Record<string, string>;

export function request<T>(path: string, method: WechatMiniprogram.RequestOption["method"],
  data?: object, extraHeaders: RequestHeaders = {}): Promise<T> {
  const token = wx.getStorageSync("investment.accessToken") as string;
  return new Promise((resolve, reject) => {
    wx.request<WechatMiniprogram.IAnyObject>({
      url: `${runtimeConfig.apiBaseUrl}${path}`,
      method,
      data: data as WechatMiniprogram.IAnyObject | undefined,
      header: {
        ...(token ? {Authorization: `Bearer ${token}`} : {}),
        ...extraHeaders
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T);
          return;
        }
        reject(response.data as ApiProblem);
      },
      fail: reject
    });
  });
}
