// Immutable edits on the workout tree, addressed by node id.

import type { WorkoutNode } from './types';

export function updateNode(nodes: WorkoutNode[], id: string, fn: (n: WorkoutNode) => WorkoutNode): WorkoutNode[] {
  return nodes.map((n) => {
    if (n.id === id) return fn(n);
    if (n.type === 'repeat') return { ...n, children: updateNode(n.children, id, fn) };
    return n;
  });
}

export function removeNode(nodes: WorkoutNode[], id: string): WorkoutNode[] {
  return nodes
    .filter((n) => n.id !== id)
    .map((n) => (n.type === 'repeat' ? { ...n, children: removeNode(n.children, id) } : n));
}

export function moveNode(nodes: WorkoutNode[], id: string, dir: -1 | 1): WorkoutNode[] {
  const i = nodes.findIndex((n) => n.id === id);
  if (i >= 0) {
    const j = i + dir;
    if (j < 0 || j >= nodes.length) return nodes;
    const copy = nodes.slice();
    [copy[i], copy[j]] = [copy[j], copy[i]];
    return copy;
  }
  return nodes.map((n) => (n.type === 'repeat' ? { ...n, children: moveNode(n.children, id, dir) } : n));
}

/** Append to the repeat with id `parentId`, or to the top level when parentId is null. */
export function appendNode(nodes: WorkoutNode[], parentId: string | null, node: WorkoutNode): WorkoutNode[] {
  if (parentId === null) return [...nodes, node];
  return updateNode(nodes, parentId, (n) => (n.type === 'repeat' ? { ...n, children: [...n.children, node] } : n));
}

/** Deep copy with fresh ids (for duplicating). */
export function cloneNode(node: WorkoutNode, newId: () => string): WorkoutNode {
  return node.type === 'step'
    ? { ...node, id: newId() }
    : { ...node, id: newId(), children: node.children.map((c) => cloneNode(c, newId)) };
}

export function insertAfter(nodes: WorkoutNode[], id: string, node: WorkoutNode): WorkoutNode[] {
  const i = nodes.findIndex((n) => n.id === id);
  if (i >= 0) return [...nodes.slice(0, i + 1), node, ...nodes.slice(i + 1)];
  return nodes.map((n) => (n.type === 'repeat' ? { ...n, children: insertAfter(n.children, id, node) } : n));
}

/** "1:30" / "90" / "1m30" -> seconds. Undefined if unparseable. */
export function parseDuration(text: string): number | undefined {
  const t = text.trim();
  let m = /^(\d+):(\d{1,2})$/.exec(t);
  if (m) return Number(m[1]) * 60 + Number(m[2]);
  m = /^(\d+)\s*m(?:in)?\s*(\d+)?\s*s?$/i.exec(t);
  if (m) return Number(m[1]) * 60 + Number(m[2] ?? 0);
  m = /^(\d+)\s*s?$/i.exec(t);
  if (m) return Number(m[1]);
  return undefined;
}
