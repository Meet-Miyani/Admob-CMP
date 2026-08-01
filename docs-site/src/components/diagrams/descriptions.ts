import descriptions from './descriptions.json';

export interface DiagramDescription {
  /** Stable slug. Matches the JSON key, the SVG title/desc id prefix, the arrow
   *  marker id prefix, and the anchor on /reference/diagrams-in-words/. */
  id: string;
  /** Shown in the figure caption and used as the SVG <title>. */
  title: string;
  /** One sentence. Used as the SVG <desc>. */
  desc: string;
  /** admob-cmp/CLAUDE.md invariant numbers this diagram encodes. */
  invariants: number[];
  /** Paragraphs of the text alternative, joined with a blank line. */
  prose: string[];
}

export const diagramDescriptions = descriptions as Record<string, DiagramDescription>;

export function getDiagram(id: string): DiagramDescription {
  const description = diagramDescriptions[id];
  if (!description) {
    throw new Error(
      `Unknown diagram id "${id}". Add an entry to src/components/diagrams/descriptions.json.`
    );
  }
  return description;
}
