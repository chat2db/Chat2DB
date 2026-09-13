import createRequest from './base';
import type { AgentOutputPage, AgentOutputQuery } from '@/types/agentOutput';

export const agentOutputUrl = (sessionId: string, artifactId: string) =>
  `/api/v3/ai/sessions/${encodeURIComponent(sessionId)}/outputs/${encodeURIComponent(artifactId)}`;

interface OutputSearchPage {
  matches: { line: number; content: string; byteOffset: number }[];
  nextCursor?: string | null;
  hasMore: boolean;
  warning?: string | null;
}

export const readAgentOutput = async (
  sessionId: string, artifactId: string, query: AgentOutputQuery, signal: AbortSignal,
): Promise<AgentOutputPage> => {
  const url = agentOutputUrl(sessionId, artifactId);
  if (query.pattern) {
    const page = await createRequest<{
      pattern: string; cursor?: string; limit: number; literal: boolean; ignoreCase: boolean;
    }, OutputSearchPage>(`${url}/search`, { errorLevel: false })(
      { pattern: query.pattern, cursor: query.cursor, limit: 100, literal: true, ignoreCase: true }, { signal },
    );
    return { content: page.matches.map((match) => `${match.line}: ${match.content}`).join('\n'),
      nextCursor: page.nextCursor, hasMore: page.hasMore, warning: page.warning };
  }
  return createRequest<{ cursor?: string; limit: number }, AgentOutputPage>(`${url}/read`, { errorLevel: false })(
    { cursor: query.cursor, limit: 100 }, { signal },
  );
};

export const downloadAgentOutputToDesktop = (sessionId: string, artifactId: string, signal: AbortSignal) =>
  createRequest<void, string | null>(`${agentOutputUrl(sessionId, artifactId)}/download-path`, {
    method: 'post', errorLevel: false, timeout: false,
  })(undefined, { signal });
