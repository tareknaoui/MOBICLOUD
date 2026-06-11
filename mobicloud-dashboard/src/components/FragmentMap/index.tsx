import { useState, useEffect } from 'react';
import { BASE } from '../../services/api';

interface Fragment {
  nodeId: string;
  index: number;
  sizeBytes: number;
  nodeConnected: boolean;
  nodeExists: boolean;
}

interface FileEntry {
  fileId: string;
  fileName: string;
  mimeType: string;
  totalSize: number;
  k: number;
  n: number;
  uploadedAt: number;
  fragments: Fragment[];
  onlineFragments: number;
  recoverable: boolean;
}

function fmtSize(b: number) {
  if (b >= 1_000_000_000) return (b / 1_000_000_000).toFixed(1) + ' GB';
  if (b >= 1_000_000)     return (b / 1_000_000).toFixed(1) + ' MB';
  if (b >= 1_000)         return (b / 1_000).toFixed(0) + ' KB';
  return b + ' B';
}

function mimeIcon(mime: string) {
  if (mime.startsWith('image/'))       return 'IMG';
  if (mime.startsWith('video/'))       return 'VID';
  if (mime.startsWith('audio/'))       return 'AUD';
  if (mime === 'application/pdf')      return 'PDF';
  if (mime === 'application/zip')      return 'ZIP';
  return 'FILE';
}

function FragmentRow({ file, topology }: { file: FileEntry; topology: Map<string, boolean> }) {
  const [open, setOpen] = useState(false);

  const aliveCount = file.fragments.filter(f => topology.get(f.nodeId) ?? f.nodeConnected).length;

  const danger = aliveCount < file.k;
  const warn   = !danger && aliveCount < file.n;

  const statusColor  = danger ? 'var(--accent-red)' : warn ? 'var(--accent-orange)' : 'var(--accent-green)';
  const statusLabel  = danger ? 'PERDU' : warn ? 'DÉGRADÉ' : 'SÉCURISÉ';

  return (
    <div style={{ borderBottom: '1px solid var(--border)', paddingBottom: 6, marginBottom: 4 }}>
      {/* ligne résumé */}
      <div
        onClick={() => setOpen(o => !o)}
        style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', padding: '4px 2px', borderRadius: 4 }}
      >
        <span style={{ fontSize: 9, fontFamily: 'monospace', background: 'var(--bg-card2)', color: 'var(--text-sec)', padding: '1px 4px', borderRadius: 3, flexShrink: 0 }}>{mimeIcon(file.mimeType)}</span>
        <span style={{ flex: 1, fontSize: 11, fontWeight: 600, color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {file.fileName}
        </span>
        <span style={{ fontSize: 10, color: 'var(--text-dim)' }}>{fmtSize(file.totalSize)}</span>
        <span style={{ fontSize: 10, color: 'var(--text-sec)' }}>k={file.k}/{file.n}</span>
        <span style={{ fontSize: 10, fontWeight: 700, color: statusColor, minWidth: 62, textAlign: 'right' }}>
          {aliveCount}/{file.n} {statusLabel}
        </span>
        <span style={{ fontSize: 10, color: 'var(--text-dim)' }}>{open ? '▲' : '▼'}</span>
      </div>

      {/* détail fragments */}
      {open && (
        <div style={{ marginTop: 6, display: 'flex', flexWrap: 'wrap', gap: 5, paddingLeft: 22 }}>
          {file.fragments.map(fr => {
            const alive = topology.get(fr.nodeId) ?? fr.nodeConnected;
            const bg    = alive ? '#14532d' : '#450a0a';
            const border = alive ? '#16a34a' : '#ef4444';
            const label  = alive ? 'EN LIGNE' : 'HORS LIGNE';
            return (
              <div key={fr.index} title={`${fr.nodeId} — ${fmtSize(fr.sizeBytes)}`} style={{
                background: bg, border: `1px solid ${border}`, borderRadius: 4,
                padding: '3px 7px', fontSize: 10, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, minWidth: 64,
              }}>
                <span style={{ fontWeight: 700, color: '#fff' }}>#{fr.index}</span>
                <span style={{ fontFamily: 'monospace', color: '#ccc', fontSize: 9 }}>{fr.nodeId.slice(0, 8)}</span>
                <span style={{ color: alive ? '#4ade80' : '#f87171', fontSize: 9, fontWeight: 600 }}>{label}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function FragmentMap({ nodeStatuses }: { nodeStatuses: Map<string, boolean> }) {
  const [files, setFiles]   = useState<FileEntry[]>([]);
  const [open, setOpen]     = useState(true);
  const [error, setError]   = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const r = await fetch(`${BASE}/metrics/fragments`);
        if (!r.ok) throw new Error();
        const d = await r.json() as { files: FileEntry[] };
        if (!cancelled) { setFiles(d.files); setError(false); }
      } catch { if (!cancelled) setError(true); }
    }
    load();
    const id = setInterval(load, 5000);
    return () => { cancelled = true; clearInterval(id); };
  }, []);

  const recoverableCount = files.filter(f => {
    const alive = f.fragments.filter(fr => nodeStatuses.get(fr.nodeId) ?? fr.nodeConnected).length;
    return alive >= f.k;
  }).length;

  return (
    <div style={{ background: 'var(--bg-card2)', borderRadius: 8, border: '1px solid var(--border)', overflow: 'hidden' }}>
      {/* header */}
      <div
        onClick={() => setOpen(o => !o)}
        style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 14px', cursor: 'pointer', borderBottom: open ? '1px solid var(--border)' : 'none' }}
      >
        <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--accent-cyan)', flex: 1 }}>
          Fragment Map
        </span>
        {files.length > 0 && (
          <span style={{ fontSize: 10, color: 'var(--text-sec)' }}>
            {recoverableCount}/{files.length} fichiers sécurisés
          </span>
        )}
        {error && <span style={{ fontSize: 10, color: 'var(--accent-red)' }}>indisponible</span>}
        <span style={{ fontSize: 10, color: 'var(--text-dim)' }}>{open ? '▲' : '▼'}</span>
      </div>

      {/* body */}
      {open && (
        <div style={{ padding: '8px 12px', maxHeight: 320, overflowY: 'auto' }}>
          {files.length === 0 && !error && (
            <div style={{ fontSize: 11, color: 'var(--text-dim)', textAlign: 'center', padding: '12px 0' }}>
              Aucun fichier annoncé
            </div>
          )}
          {files.map(f => (
            <FragmentRow key={f.fileId} file={f} topology={nodeStatuses} />
          ))}
        </div>
      )}
    </div>
  );
}
