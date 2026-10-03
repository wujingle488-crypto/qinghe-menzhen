import { ConsultPage } from "./pages/ConsultPage";
import { KbPage } from "./pages/KbPage";
import { LoginPage } from "./pages/LoginPage";
import { ProfilePage } from "./pages/ProfilePage";
import { useEffect, useRef, useState } from "react";
import { Ico } from "./components/Ico";
import { api, consultSessionKey, rememberAccount, setAuthToken, type Account } from "./api";

type Page = "consult" | "profile" | "kb";

const PAGES: { id: Page; label: string; icon: string }[] = [
  { id: "consult", label: "问诊", icon: "/brand/chat.svg" },
  { id: "profile", label: "就诊卡", icon: "/brand/medical_card.svg" },
  { id: "kb", label: "知识库", icon: "/brand/book.svg" }
];

export function App() {
  const [account, setAccount] = useState<Account | null | undefined>(undefined);
  const [page, setPage] = useState<Page>("consult");
  const [menuOpen, setMenuOpen] = useState(false);
  const userRef = useRef<HTMLDivElement>(null);
  const [consultIntent, setConsultIntent] = useState<{ id: number | null; draft?: string; nonce: number } | null>(null);

  useEffect(() => {
    api("/cs-api/api/auth/me")
      .then(async (response) => {
        if (!response.ok) {
          rememberAccount(null);
          setAccount(null);
          return;
        }
        const next = (await response.json()) as Account;
        rememberAccount(next.id);
        setAccount(next);
      })
      .catch(() => {
        rememberAccount(null);
        setAccount(null);
      });
    const lost = () => {
      rememberAccount(null);
      setAccount(null);
      setMenuOpen(false);
    };
    window.addEventListener("qinghe-unauthorized", lost);
    return () => window.removeEventListener("qinghe-unauthorized", lost);
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: MouseEvent) => {
      if (!userRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [menuOpen]);

  function enter(next: Account) {
    rememberAccount(next.id);
    setAccount(next);
    setPage("consult");
  }

  function openConsult(id: number | null, draft?: string) {
    setConsultIntent({ id, draft, nonce: Date.now() });
    setPage("consult");
  }

  async function logout() {
    await api("/cs-api/api/auth/logout", { method: "POST" });
    localStorage.removeItem(consultSessionKey());
    setAuthToken(null);
    rememberAccount(null);
    setAccount(null);
    setMenuOpen(false);
  }

  if (account === undefined) {
    return <div className="qh-login"><p className="qh-login-wait">正在确认登录状态…</p></div>;
  }
  if (!account) {
    return <LoginPage onEnter={enter} />;
  }

  return (
    <div className="qh-shell">
      <img className="qh-leaf qh-leaf-a" src="/brand/leaf_bg.png" alt="" />
      <img className="qh-leaf qh-leaf-b" src="/brand/leaf_bg.png" alt="" />
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-white focus:px-3 focus:py-2">
        跳到主要内容
      </a>
      <header className="qh-nav">
        <div className="qh-brand">
          <img src="/brand/logo_heart.svg" alt="" />
          <div>
            <strong>青禾门诊</strong>
            <span>智能问诊 · 教学参考</span>
          </div>
        </div>
        <nav aria-label="页面">
          {PAGES.map((item) => (
            <button
              key={item.id}
              type="button"
              className={page === item.id ? "on" : ""}
              aria-current={page === item.id ? "page" : undefined}
              onClick={() => setPage(item.id)}
            >
              <Ico name={item.icon.replace("/brand/", "")} />
              {item.label}
            </button>
          ))}
          <div className="qh-user" ref={userRef}>
            <button
              type="button"
              className="qh-avatar"
              aria-label="账号菜单"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((value) => !value)}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="9" r="3.2" fill="none" stroke="currentColor" strokeWidth="1.7" />
                <path d="M6 19.2c.8-3 2.8-4.5 6-4.5s5.2 1.5 6 4.5" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
              </svg>
            </button>
            {menuOpen ? (
              <div className="qh-user-menu" role="menu">
                <strong>{account.username}</strong>
                <button type="button" role="menuitem" onClick={() => void logout()}>退出登录</button>
              </div>
            ) : null}
          </div>
        </nav>
      </header>
      <main id="main" className="qh-stage">
        {page === "consult" ? <ConsultPage onOpenProfile={() => setPage("profile")} consultIntent={consultIntent} /> : null}
        {page === "profile" ? <ProfilePage onConsult={openConsult} onBack={() => setPage("consult")} /> : null}
        {page === "kb" ? <KbPage onOpenProfile={() => setPage("profile")} /> : null}
      </main>
    </div>
  );
}
