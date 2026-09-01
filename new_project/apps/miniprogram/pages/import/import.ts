import {request} from "../../utils/api";
import {newIdempotencyKey} from "../../utils/idempotency";

type ImportMediaType = "application/json" | "application/x-sqlite3";
type UploadState = "IDLE" | "READY" | "UPLOADING" | "SCAN_QUEUED" | "RETRYABLE_ERROR";
type ImportJobStatus = "SUCCEEDED" | "FAILED" | "NEEDS_REVIEW" | "COMMITTED" | "EXPIRED";

interface UploadRequestResponse {
  importExportFileId: string;
  uploadUrl: string;
  method: "POST";
  fileField: "file";
  formData: Record<string, string>;
  expiresAt: string;
}

interface FileScanRequestResponse {
  importExportFileId: string;
  status: "QUARANTINED";
}

interface FileStatusResponse {
  importExportFileId: string;
  direction: "IMPORT" | "RECONCILIATION_EVIDENCE";
  mediaType: string;
  byteSize: string;
  status: "UPLOAD_PENDING" | "QUARANTINED" | "SCANNED" | "PREVIEWED" | "COMMITTED" | "DELETED" | "FAILED";
}

interface ImportPageData {
  filePath: string;
  fileName: string;
  mediaType: ImportMediaType | "";
  byteSize: string;
  sha256: string;
  uploadState: UploadState;
  uploadRequestKey: string;
  scanRequestKey: string;
  uploadedFileId: string;
  serverFileStatus: FileStatusResponse["status"] | "";
  snapshotId: string;
  mappingJson: string;
  importJobId: string;
  importJobStatus: ImportJobStatus | "";
  previewChecksum: string;
  previewApplicableCount: number;
  previewNeedsReviewCount: number;
  previewLines: ImportPreviewLine[];
  previewRequestKey: string;
  confirmRequestKey: string;
  errorMessage: string;
}

interface ImportPreviewLine {
  sourceRow: number;
  status: "APPLICABLE" | "NEEDS_REVIEW" | "SKIPPED" | "FAILED";
  code: string | null;
  operation: string | null;
  note: string | null;
}

interface ImportJobResponse {
  jobId: string;
  importPreviewId: string;
  importExportFileId: string;
  format: "LEGACY_DASHBOARD_JSON" | "LEGACY_SQLITE";
  sourceSnapshotId: string | null;
  previewChecksum: string;
  status: ImportJobStatus;
  expiresAt: string;
  applicableCount: number;
  needsReviewCount: number;
  lines: ImportPreviewLine[];
}

interface ImportPageMethods {
  chooseFile(): void;
  uploadFile(): Promise<void>;
  resetForFile(path: string, name: string, mediaType: ImportMediaType, byteSize: number, sha256: string): void;
  requestScan(importExportFileId: string): Promise<void>;
  restorePendingScan(): Promise<void>;
  refreshScanStatus(): Promise<void>;
  onMappingInput(event: WechatMiniprogram.Input): void;
  onSnapshotInput(event: WechatMiniprogram.Input): void;
  createPreview(): Promise<void>;
  restorePendingJob(): Promise<void>;
  refreshImportJob(): Promise<void>;
  confirmImport(): Promise<void>;
  applyPreview(result: ImportJobResponse): void;
}

const CLIENT_DEFAULT_MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
const SUPPORTED_EXTENSIONS = ["json", "sqlite", "sqlite3", "db"];

Page<ImportPageData, ImportPageMethods>({
  data: {
    filePath: "",
    fileName: "",
    mediaType: "",
    byteSize: "",
    sha256: "",
    uploadState: "IDLE",
    uploadRequestKey: "",
    scanRequestKey: "",
    uploadedFileId: "",
    serverFileStatus: "",
    snapshotId: "",
    mappingJson: defaultMappingJson(),
    importJobId: "",
    importJobStatus: "",
    previewChecksum: "",
    previewApplicableCount: 0,
    previewNeedsReviewCount: 0,
    previewLines: [],
    previewRequestKey: "",
    confirmRequestKey: "",
    errorMessage: ""
  },

  onShow() {
    if (!wx.getStorageSync("investment.accessToken")) {
      wx.reLaunch({url: "/pages/login/login"});
      return;
    }
    this.restorePendingScan();
    this.restorePendingJob();
  },

  chooseFile() {
    wx.chooseMessageFile({
      count: 1,
      type: "file",
      extension: SUPPORTED_EXTENSIONS,
      success: (result) => {
        const selected = result.tempFiles[0];
        const mediaType = mediaTypeFor(selected.name);
        if (!mediaType) {
          this.setData({errorMessage: "仅支持 JSON 或 SQLite（.sqlite、.sqlite3、.db）文件。"});
          return;
        }
        wx.getFileSystemManager().getFileInfo({
          filePath: selected.path,
          digestAlgorithm: "sha256",
          success: (info) => {
            if (info.size < 1) {
              this.setData({errorMessage: "不能上传空文件。"});
              return;
            }
            if (info.size > CLIENT_DEFAULT_MAX_UPLOAD_BYTES) {
              this.setData({errorMessage: "文件超过本地默认上限 10 MiB；服务端也会再次校验。"});
              return;
            }
            const sha256 = info.digest.toLowerCase();
            if (!/^[a-f0-9]{64}$/.test(sha256)) {
              this.setData({errorMessage: "无法取得文件 SHA-256 摘要，请重新选择文件。"});
              return;
            }
            this.resetForFile(selected.path, selected.name, mediaType, info.size, sha256);
          },
          fail: () => this.setData({errorMessage: "无法读取文件信息或计算 SHA-256 摘要。"})
        });
      },
      fail: (error) => {
        const message = errorMessage(error);
        if (message) {
          this.setData({errorMessage: message});
        }
      }
    });
  },

  async uploadFile() {
    if (!this.data.filePath || !this.data.mediaType || !this.data.byteSize || !this.data.sha256) {
      this.setData({errorMessage: "请先选择一个 JSON 或 SQLite 文件。"});
      return;
    }
    this.setData({uploadState: "UPLOADING", errorMessage: ""});
    try {
      if (this.data.uploadedFileId) {
        await this.requestScan(this.data.uploadedFileId);
        return;
      }
      const uploadRequestKey = this.data.uploadRequestKey || newIdempotencyKey();
      this.setData({uploadRequestKey});
      const credential = await request<UploadRequestResponse>("/api/v1/files/upload-requests", "POST", {
        direction: "IMPORT",
        mediaType: this.data.mediaType,
        byteSize: this.data.byteSize,
        sha256: this.data.sha256
      }, {"Idempotency-Key": uploadRequestKey});
      await uploadToPresignedPost(credential, this.data.filePath);
      this.setData({uploadedFileId: credential.importExportFileId});
      await this.requestScan(credential.importExportFileId);
    } catch (error) {
      this.setData({
        uploadState: "RETRYABLE_ERROR",
        errorMessage: errorMessage(error) || "上传或扫描申请失败；请确认网络后使用原请求键手动重试。"
      });
    }
  },

  async requestScan(importExportFileId) {
    const scanRequestKey = this.data.scanRequestKey || newIdempotencyKey();
    this.setData({scanRequestKey});
    const result = await request<FileScanRequestResponse>(
      `/api/v1/files/${importExportFileId}/scan-requests`, "POST", undefined, {"Idempotency-Key": scanRequestKey});
    this.setData({
      uploadState: "SCAN_QUEUED",
      uploadedFileId: result.importExportFileId,
      serverFileStatus: result.status,
      uploadRequestKey: "",
      scanRequestKey: "",
      errorMessage: ""
    });
    wx.setStorageSync("investment.pendingImportFileId", result.importExportFileId);
    wx.showToast({title: "已申请服务器扫描", icon: "success"});
  },

  async restorePendingScan() {
    if (this.data.uploadedFileId) {
      return;
    }
    const importExportFileId = wx.getStorageSync("investment.pendingImportFileId") as string;
    if (!importExportFileId) {
      return;
    }
    this.setData({uploadedFileId: importExportFileId, serverFileStatus: "QUARANTINED", uploadState: "SCAN_QUEUED"});
    await this.refreshScanStatus();
  },

  async refreshScanStatus() {
    if (!this.data.uploadedFileId) {
      return;
    }
    try {
      const result = await request<FileStatusResponse>(`/api/v1/files/${this.data.uploadedFileId}`, "GET");
      if (result.direction !== "IMPORT") {
        throw new Error("该文件不是历史导入文件。");
      }
      if (result.status === "FAILED" || result.status === "DELETED") {
        wx.removeStorageSync("investment.pendingImportFileId");
        this.setData({
          uploadedFileId: "",
          serverFileStatus: result.status,
          uploadState: "IDLE",
          errorMessage: result.status === "FAILED"
            ? "服务器扫描失败。请重新选择文件并重新上传。"
            : "文件已过保留期删除。请重新选择文件并重新上传。"
        });
        return;
      }
      this.setData({serverFileStatus: result.status, uploadState: "SCAN_QUEUED", errorMessage: ""});
    } catch (error) {
      this.setData({errorMessage: errorMessage(error) || "无法查询服务器扫描状态。"});
    }
  },

  onMappingInput(event) {
    this.setData({mappingJson: event.detail.value, errorMessage: ""});
  },

  onSnapshotInput(event) {
    this.setData({snapshotId: event.detail.value.trim(), errorMessage: ""});
  },

  async createPreview() {
    if (this.data.serverFileStatus !== "SCANNED" && this.data.serverFileStatus !== "PREVIEWED") {
      this.setData({errorMessage: "请先等待服务器扫描成功。"});
      return;
    }
    let mappings: Record<string, unknown>;
    try {
      mappings = JSON.parse(this.data.mappingJson) as Record<string, unknown>;
    } catch {
      this.setData({errorMessage: "映射 JSON 格式无效。"});
      return;
    }
    const format = this.data.mediaType === "application/x-sqlite3" ? "LEGACY_SQLITE" : "LEGACY_DASHBOARD_JSON";
    if (format === "LEGACY_SQLITE" && !this.data.snapshotId) {
      this.setData({errorMessage: "SQLite 导入必须填写 snapshots.id。"});
      return;
    }
    const previewRequestKey = this.data.previewRequestKey || newIdempotencyKey();
    this.setData({previewRequestKey, errorMessage: ""});
    try {
      const result = await request<ImportJobResponse>("/api/v1/ledger/imports", "POST", {
        importExportFileId: this.data.uploadedFileId,
        format,
        snapshotId: format === "LEGACY_SQLITE" ? this.data.snapshotId : null,
        currencyMappings: mappings.currencyMappings || [],
        instrumentMappings: mappings.instrumentMappings || [],
        dividendEntitlementOverrides: mappings.dividendEntitlementOverrides || [],
        optionExpiryAttestations: mappings.optionExpiryAttestations || []
      }, {"Idempotency-Key": previewRequestKey});
      this.applyPreview(result);
      this.setData({previewRequestKey: ""});
      wx.setStorageSync("investment.pendingImportJobId", result.jobId);
    } catch (error) {
      this.setData({errorMessage: errorMessage(error) || "无法创建 dry-run 预览。"});
    }
  },

  async restorePendingJob() {
    if (this.data.importJobId) return;
    const jobId = wx.getStorageSync("investment.pendingImportJobId") as string;
    if (!jobId) return;
    this.setData({importJobId: jobId});
    await this.refreshImportJob();
  },

  async refreshImportJob() {
    if (!this.data.importJobId) return;
    try {
      const result = await request<ImportJobResponse>(`/api/v1/jobs/${this.data.importJobId}`, "GET");
      this.applyPreview(result);
      if (result.status === "COMMITTED" || result.status === "EXPIRED") {
        wx.removeStorageSync("investment.pendingImportJobId");
      }
    } catch (error) {
      this.setData({errorMessage: errorMessage(error) || "无法查询 dry-run 作业。"});
    }
  },

  async confirmImport() {
    if (this.data.importJobStatus !== "SUCCEEDED" || !this.data.importJobId || !this.data.previewChecksum) {
      this.setData({errorMessage: "仅能确认无待处理行的有效预览。"});
      return;
    }
    const confirmRequestKey = this.data.confirmRequestKey || newIdempotencyKey();
    this.setData({confirmRequestKey, errorMessage: ""});
    try {
      const result = await request<ImportJobResponse>(`/api/v1/ledger/imports/${this.data.importJobId}/confirm`, "POST", {
        expectedChecksum: this.data.previewChecksum
      }, {"Idempotency-Key": confirmRequestKey});
      this.applyPreview(result);
      this.setData({confirmRequestKey: ""});
      wx.removeStorageSync("investment.pendingImportJobId");
      wx.showToast({title: "已确认导入", icon: "success"});
    } catch (error) {
      this.setData({errorMessage: errorMessage(error) || "确认导入失败；请勿修改 checksum 后重试。"});
    }
  },

  applyPreview(result: ImportJobResponse) {
    this.setData({
      importJobId: result.jobId,
      importJobStatus: result.status,
      previewChecksum: result.previewChecksum,
      previewApplicableCount: result.applicableCount,
      previewNeedsReviewCount: result.needsReviewCount,
      previewLines: result.lines,
      serverFileStatus: result.status === "COMMITTED" ? "COMMITTED" : "PREVIEWED",
      errorMessage: ""
    });
  },

  resetForFile(path, name, mediaType, byteSize, sha256) {
    this.setData({
      filePath: path,
      fileName: name,
      mediaType,
      byteSize: String(byteSize),
      sha256,
      uploadState: "READY",
      uploadRequestKey: "",
      scanRequestKey: "",
      uploadedFileId: "",
      serverFileStatus: "",
      snapshotId: "",
      importJobId: "",
      importJobStatus: "",
      previewChecksum: "",
      previewApplicableCount: 0,
      previewNeedsReviewCount: 0,
      previewLines: [],
      previewRequestKey: "",
      confirmRequestKey: "",
      errorMessage: ""
    });
    wx.removeStorageSync("investment.pendingImportFileId");
  }
});

function defaultMappingJson(): string {
  return JSON.stringify({
    currencyMappings: [],
    instrumentMappings: [],
    dividendEntitlementOverrides: [],
    optionExpiryAttestations: []
  }, null, 2);
}

function mediaTypeFor(fileName: string): ImportMediaType | null {
  const suffix = fileName.split(".").pop()?.toLowerCase();
  if (suffix === "json") {
    return "application/json";
  }
  if (suffix === "sqlite" || suffix === "sqlite3" || suffix === "db") {
    return "application/x-sqlite3";
  }
  return null;
}

function uploadToPresignedPost(credential: UploadRequestResponse, filePath: string): Promise<void> {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: credential.uploadUrl,
      filePath,
      name: credential.fileField,
      formData: credential.formData,
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve();
          return;
        }
        reject(new Error(`对象存储拒绝上传（HTTP ${response.statusCode}）。`));
      },
      fail: reject
    });
  });
}

function errorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null && "message" in error
      && typeof (error as {message?: unknown}).message === "string") {
    return (error as {message: string}).message;
  }
  return "";
}
