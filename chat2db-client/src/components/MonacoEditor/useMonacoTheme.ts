import { useEffect } from 'react';
import {useTheme} from '@/hooks/useTheme';
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import { ThemeType } from '@/constants';
 
// 如果用户点击的不是可复制的元素，就清空选中的内容
function useMonacoTheme() {
  const [appTheme] = useTheme();
  // 监听主题色变化切换编辑器主题色
  useEffect(() => {
    const { colorPrimary, colorBgBase, colorTextBase  } = window._AppThemePack || {};

    const colors = {
      'editor.lineHighlightBackground': colorPrimary + '14', // 当前行背景色
      'editor.selectionBackground': colorPrimary + '50', // 选中文本的背景色
      // 'editorLineNumber.foreground': colorPrimary, // 行号颜色
      'editorLineNumber.activeForeground': colorPrimary, // 当前行号颜色
      // 'editorCursor.foreground': colorPrimary, // 光标颜色
      'editorRuler.foreground': colorPrimary + '15', // 右侧竖线颜色
      'editor.foreground': colorTextBase, // 文本颜色
      'editor.background': colorBgBase, //背景色
    };

    monaco.editor.defineTheme(appTheme.backgroundColor, {
      // base 如果appTheme.backgroundColor包含dark则为vs-dark，否则为vs
      base: appTheme.backgroundColor.includes(ThemeType.Dark) ? 'vs-dark' : 'vs',
      inherit: true, // 是否继承vscode默认主题
      rules: [{ background: '#15161a' }] as any,
      colors,
    });
    
    monaco.editor.setTheme(appTheme.backgroundColor);
  }, [appTheme.backgroundColor]);

}

export default useMonacoTheme;
