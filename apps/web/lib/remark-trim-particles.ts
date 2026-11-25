interface Node {
  type: string;
  url?: string;
  value?: string;
  children?: Node[];
}

// 주소에 붙여 쓴 조사. 주소에 원래 있는 한글(위키 문서 이름 등)은 건드리지 않는다.
const PARTICLE =
  /^(https?:\/\/.*[\x21-\x7e]|www\..*[\x21-\x7e])((?:으로|로|에서|에|을|를|은|는|이|가|와|과|도|만|까지|부터)(?:는|도|만)?)$/i;

function isAutolink(node: Node) {
  const [child, ...rest] = node.children ?? [];
  return (
    node.type === 'link' &&
    rest.length === 0 &&
    child?.type === 'text' &&
    (node.url === child.value || node.url === `http://${child.value}`)
  );
}

function trim(nodes: Node[]): Node[] {
  return nodes.flatMap((node) => {
    const children = node.children ? trim(node.children) : undefined;
    const match = isAutolink(node) ? PARTICLE.exec(node.children?.[0]?.value ?? '') : null;
    if (!match) {
      return [children ? { ...node, children } : node];
    }
    const [, address = '', particle = ''] = match;
    return [
      {
        ...node,
        url: node.url?.slice(0, -particle.length),
        children: [{ type: 'text', value: address }],
      },
      { type: 'text', value: particle },
    ];
  });
}

/** 자동 링크 뒤에 붙은 조사를 링크 밖으로 뺀다. */
export function remarkTrimParticles() {
  return (tree: Node) => {
    tree.children = trim(tree.children ?? []);
  };
}
