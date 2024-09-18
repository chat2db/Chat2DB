import i18n from '@/i18n';
import { IAiConfig } from '@/typings';
import { AIType } from '@/typings/ai';

export type IAiConfigBooleans = {
  [K in keyof IAiConfig]?: boolean | string;
};

const AITypeName = {
  [AIType.CHAT2DBAI]: 'Chat2DB',
  [AIType.ZHIPUAI]: i18n('setting.tab.aiType.zhipu'),
  [AIType.BAICHUANAI]: i18n('setting.tab.aiType.baichuan'),
  [AIType.WENXINAI]: i18n('setting.tab.aiType.wenxin'),
  [AIType.TONGYIQIANWENAI]: i18n('setting.tab.aiType.tongyiqianwen'),
  [AIType.OPENAI]: 'Open AI',
  [AIType.AZUREAI]: 'Azure AI',
  [AIType.RESTAI]: i18n('setting.tab.custom'),
};

const AIFormConfig: Record<AIType, IAiConfigBooleans> = {
  [AIType.CHAT2DBAI]: {
    apiKey: true,
  },
  [AIType.ZHIPUAI]: {
    apiKey: true,
    apiHost: 'https://open.bigmodel.cn/api/paas/v4/chat/completions',
    model: 'codegeex-4',
  },
  [AIType.BAICHUANAI]: {
    apiKey: true,
    secretKey: true,
    apiHost: 'https://api.baichuan-ai.com/v1/stream/chat/',
    model: 'Baichuan2-53B',
  },
  [AIType.WENXINAI]: {
    apiKey: true,
    apiHost: true,
  },
  [AIType.TONGYIQIANWENAI]: {
    apiKey: true,
    apiHost: true,
    model: true,
  },
  [AIType.OPENAI]: {
    apiKey: true,
    apiHost: 'https://api.openai.com/',
    httpProxyHost: true,
    httpProxyPort: true,
    // model: 'gpt-3.5-turbo',
  },
  [AIType.AZUREAI]: {
    apiKey: true,
    apiHost: true,
    model: true,
  },
  [AIType.RESTAI]: {
    apiKey: true,
    apiHost: true,
    model: true,
  },
};

export { AIFormConfig, AITypeName };
