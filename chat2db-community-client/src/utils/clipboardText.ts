export function normalizeClipboardText(text: string, userAgent: string) {
  // Windows text clipboard formats require CRLF, including for legacy native applications.
  return /Windows|Win32|Win64/i.test(userAgent) ? text.replace(/\r\n|\r|\n/g, '\r\n') : text;
}
