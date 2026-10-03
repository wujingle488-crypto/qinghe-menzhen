export type Account = { id: number; username: string };

const TOKEN_KEY = "qinghe-auth";

let accountId: number | null = null;

export function rememberAccount(id: number | null) {
  accountId = id;
}

export function consultSessionKey() {
  return accountId == null ? "qinghe-consult-session" : `qinghe-consult-session:${accountId}`;
}

export function authToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setAuthToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export async function api(path: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers);
  const token = authToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(path, { ...init, headers });
  if (response.status === 401) {
    setAuthToken(null);
    window.dispatchEvent(new Event("qinghe-unauthorized"));
  }
  return response;
}

export async function errorText(response: Response) {
  try {
    const body = (await response.json()) as { error?: string };
    return body.error || "请求失败";
  } catch {
    return "请求失败";
  }
}
