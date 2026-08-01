import { describe, expect, it } from 'vitest';

type Node = {
  type: string;
  tagName?: string;
  properties?: Record<string, unknown>;
  children?: Node[];
  value?: string;
};

async function loadTransform() {
  const module = await import('../src/lib/rehype-table-scroll.mjs').catch(() => null);
  expect(module?.default).toBeTypeOf('function');
  return module!.default as () => (tree: Node) => void;
}

function table(columns: number): Node {
  return {
    type: 'element',
    tagName: 'table',
    children: [
      {
        type: 'element',
        tagName: 'thead',
        children: [
          {
            type: 'element',
            tagName: 'tr',
            children: Array.from({ length: columns }, (_, index) => ({
              type: 'element',
              tagName: 'th',
              children: [{ type: 'text', value: `Column ${index + 1}` }],
            })),
          },
        ],
      },
      {
        type: 'element',
        tagName: 'tbody',
        children: [
          {
            type: 'element',
            tagName: 'tr',
            children: Array.from({ length: columns }, (_, index) => ({
              type: 'element',
              tagName: 'td',
              children: [{ type: 'text', value: `Value ${index + 1}` }],
            })),
          },
        ],
      },
    ],
  };
}

function root(...children: Node[]): Node {
  return { type: 'root', children };
}

describe('rehypeTableScroll', () => {
  it('wraps a two-column Markdown table in one accessible scroll region', async () => {
    const transform = (await loadTransform())();
    const source = table(2);
    const tree = root(source);

    transform(tree);

    const wrapper = tree.children?.[0];
    expect(wrapper).toMatchObject({
      type: 'element',
      tagName: 'div',
      properties: {
        className: ['table-scroll'],
        tabIndex: 0,
        role: 'region',
        ariaLabel: 'Scrollable data table',
      },
      children: [source],
    });
  });

  it('marks four-column tables as wide while preserving every child', async () => {
    const transform = (await loadTransform())();
    const source = table(4);
    const tree = root(source);

    transform(tree);

    const wrapper = tree.children?.[0];
    expect(wrapper?.properties?.className).toEqual(['table-scroll', 'table-scroll--wide']);
    expect(wrapper?.children?.[0]).toBe(source);
    expect(source.children?.[0].children?.[0].children).toHaveLength(4);
  });

  it('does not wrap a table that already sits in a table-scroll region', async () => {
    const transform = (await loadTransform())();
    const source = table(2);
    const wrapper: Node = {
      type: 'element',
      tagName: 'div',
      properties: { className: ['table-scroll'], tabIndex: 0 },
      children: [source],
    };
    const tree = root(wrapper);

    transform(tree);

    expect(tree.children).toEqual([wrapper]);
    expect(wrapper.children).toEqual([source]);
  });
});
