import type { PhoneAuthorizeResponse, StudentMe, StudentSession } from "../types/api";

const SESSION_KEY = "s9_student_session";
const MOCK_OPENID_KEY = "s9_mock_openid";
const MOCK_PHONE_KEY = "s9_mock_phone";

export type StoredSession = {
  accessToken: string;
  studentId: number;
  studentNo: string;
  mobileBound: boolean;
  mobile?: string;
  nickname?: string;
  expireAt?: string;
};

export function getStoredSession(): StoredSession | null {
  const value = wx.getStorageSync<StoredSession | "">(SESSION_KEY);
  return value && typeof value === "object" ? value : null;
}

export function saveLoginSession(response: StudentSession): StoredSession {
  const session: StoredSession = {
    accessToken: response.access_token,
    studentId: response.student_id,
    studentNo: response.student_no,
    mobileBound: response.mobile_bound,
    mobile: response.mobile,
    expireAt: response.expire_at
  };
  wx.setStorageSync(SESSION_KEY, session);
  return session;
}

export function updateSessionFromMe(me: StudentMe): StoredSession | null {
  const current = getStoredSession();
  if (!current) {
    return null;
  }
  const next: StoredSession = {
    ...current,
    studentId: me.student_id,
    studentNo: me.student_no,
    mobile: me.mobile,
    mobileBound: Boolean(me.mobile),
    nickname: me.nickname
  };
  wx.setStorageSync(SESSION_KEY, next);
  return next;
}

export function updateSessionFromPhone(response: PhoneAuthorizeResponse): StoredSession | null {
  const current = getStoredSession();
  if (!current) {
    return null;
  }
  const next: StoredSession = {
    ...current,
    studentId: response.student_id,
    studentNo: response.student_no,
    mobile: response.mobile,
    mobileBound: response.mobile_bound
  };
  wx.setStorageSync(SESSION_KEY, next);
  return next;
}

export function clearSession(): void {
  wx.removeStorageSync(SESSION_KEY);
}

export function getAccessToken(): string {
  return getStoredSession()?.accessToken ?? "";
}

export function getOrCreateMockOpenid(): string {
  const existing = wx.getStorageSync<string | "">(MOCK_OPENID_KEY);
  if (existing) {
    return existing;
  }
  const value = `mock:s9_student_${Date.now().toString(16)}`;
  wx.setStorageSync(MOCK_OPENID_KEY, value);
  return value;
}

export function getMockPhone(): string {
  return wx.getStorageSync<string | "">(MOCK_PHONE_KEY) || "13900000009";
}

export function setMockPhone(value: string): void {
  wx.setStorageSync(MOCK_PHONE_KEY, value);
}
