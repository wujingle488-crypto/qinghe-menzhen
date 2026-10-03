import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Ico } from "./Ico";

export type HistoryItem = { id: number; title: string; updatedAt?: string };

type Props = {
  history: HistoryItem[];
  activeId: number | null;
  onOpen: (id: number) => void;
  onRename: (id: number, title: string) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onDeleteMany: (ids: number[]) => Promise<void>;
  whenLabel: (value?: string) => string;
  historyIcon: (title: string) => string;
  historyTone: (title: string) => string;
};

export function HistorySideList({
  history,
  activeId,
  onOpen,
  onRename,
  onDelete,
  onDeleteMany,
  whenLabel,
  historyIcon,
  historyTone
}: Props) {
  const [menuId, setMenuId] = useState<number | null>(null);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number } | null>(null);
  const [selectMode, setSelectMode] = useState(false);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [renameId, setRenameId] = useState<number | null>(null);
  const [renameText, setRenameText] = useState("");
  const [busy, setBusy] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const renameRef = useRef<HTMLInputElement>(null);

  function closeMenu() {
    setMenuId(null);
    setMenuPos(null);
  }

  function openMenu(id: number, anchor: HTMLElement) {
    const rect = anchor.getBoundingClientRect();
    const menuWidth = 140;
    const menuHeight = 132;
    const left = Math.min(Math.max(8, rect.right - menuWidth), window.innerWidth - menuWidth - 8);
    const openUp = rect.bottom + menuHeight + 8 > window.innerHeight;
    const top = openUp
      ? Math.max(8, rect.top - menuHeight - 6)
      : Math.min(window.innerHeight - menuHeight - 8, rect.bottom + 6);
    setMenuId(id);
    setMenuPos({ top, left });
  }

  useEffect(() => {
    if (!menuId) return;
    const close = (event: MouseEvent) => {
      const target = event.target as Node;
      if (menuRef.current?.contains(target)) return;
      if ((target as HTMLElement).closest?.(".qh-more-btn")) return;
      closeMenu();
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") closeMenu();
    };
    const onScroll = () => closeMenu();
    window.addEventListener("mousedown", close);
    window.addEventListener("keydown", onKey);
    window.addEventListener("resize", onScroll);
    document.querySelector(".qh-history")?.addEventListener("scroll", onScroll);
    return () => {
      window.removeEventListener("mousedown", close);
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("resize", onScroll);
      document.querySelector(".qh-history")?.removeEventListener("scroll", onScroll);
    };
  }, [menuId]);

  useEffect(() => {
    if (renameId != null) {
      renameRef.current?.focus();
      renameRef.current?.select();
    }
  }, [renameId]);

  function leaveSelectMode() {
    setSelectMode(false);
    setSelected(new Set());
  }

  function toggleSelect(id: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function commitRename() {
    if (renameId == null || busy) return;
    const next = renameText.trim();
    if (!next) {
      setRenameId(null);
      return;
    }
    setBusy(true);
    try {
      await onRename(renameId, next);
      setRenameId(null);
    } finally {
      setBusy(false);
    }
  }

  async function removeOne(id: number) {
    closeMenu();
    setBusy(true);
    try {
      await onDelete(id);
    } finally {
      setBusy(false);
    }
  }

  const activeMenuItem = menuId == null ? null : history.find((item) => item.id === menuId) ?? null;
  const menuPortal =
    activeMenuItem && menuPos
      ? createPortal(
          <div
            ref={menuRef}
            className="qh-hist-menu"
            role="menu"
            style={{ top: menuPos.top, left: menuPos.left }}
          >
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                closeMenu();
                setRenameText(activeMenuItem.title);
                setRenameId(activeMenuItem.id);
              }}
            >
              <Ico name="pencil.svg" />
              重命名
            </button>
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                closeMenu();
                setSelectMode(true);
                setSelected(new Set([activeMenuItem.id]));
              }}
            >
              <Ico name="checklist.svg" />
              多选
            </button>
            <button
              type="button"
              role="menuitem"
              className="danger"
              onClick={() => void removeOne(activeMenuItem.id)}
            >
              <Ico name="trash.svg" />
              删除
            </button>
          </div>,
          document.body
        )
      : null;

  async function removeSelected() {
    const ids = [...selected];
    if (ids.length === 0) return;
    setBusy(true);
    try {
      await onDeleteMany(ids);
      leaveSelectMode();
    } finally {
      setBusy(false);
    }
  }

  if (history.length === 0) {
    return <p className="qh-hist-empty">还没有问诊记录</p>;
  }

  return (
    <>
      <ul className={`qh-history${selectMode ? " is-selecting" : ""}`}>
        {history.map((item) => {
          const checked = selected.has(item.id);
          const renaming = renameId === item.id;
          return (
            <li key={item.id} className={menuId === item.id ? "menu-open" : ""}>
              {selectMode ? (
                <label className={`qh-hist-check${checked ? " on" : ""}`}>
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggleSelect(item.id)}
                  />
                  <span className="qh-hist-check-box" aria-hidden="true" />
                </label>
              ) : null}
              <button
                type="button"
                className={activeId === item.id ? "active" : ""}
                onClick={() => {
                  if (selectMode) {
                    toggleSelect(item.id);
                    return;
                  }
                  if (renaming) return;
                  onOpen(item.id);
                }}
              >
                <span className={`qh-hist-ico ${historyTone(item.title)}`}>
                  <Ico name={historyIcon(item.title)} />
                </span>
                <span className="qh-hist-copy">
                  {renaming ? (
                    <input
                      ref={renameRef}
                      className="qh-hist-rename"
                      value={renameText}
                      maxLength={40}
                      disabled={busy}
                      aria-label="重命名对话"
                      onClick={(event) => event.stopPropagation()}
                      onChange={(event) => setRenameText(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          void commitRename();
                        }
                        if (event.key === "Escape") {
                          event.preventDefault();
                          setRenameId(null);
                        }
                      }}
                      onBlur={() => void commitRename()}
                    />
                  ) : (
                    <>
                      <b>{item.title}</b>
                      <small>{whenLabel(item.updatedAt)}</small>
                    </>
                  )}
                </span>
              </button>
              {!selectMode && !renaming ? (
                <div className="qh-hist-more">
                  <button
                    type="button"
                    className="qh-more-btn"
                    aria-label={`更多操作「${item.title}」`}
                    aria-expanded={menuId === item.id}
                    onClick={(event) => {
                      event.stopPropagation();
                      if (menuId === item.id) {
                        closeMenu();
                        return;
                      }
                      openMenu(item.id, event.currentTarget);
                    }}
                  >
                    <Ico name="more.svg" />
                  </button>
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
      {selectMode ? (
        <div className="qh-hist-select-bar">
          <span>已选 {selected.size} 条</span>
          <div>
            <button type="button" disabled={busy} onClick={leaveSelectMode}>取消</button>
            <button
              type="button"
              className="danger"
              disabled={busy || selected.size === 0}
              onClick={() => void removeSelected()}
            >
              删除
            </button>
          </div>
        </div>
      ) : null}
      {menuPortal}
    </>
  );
}
