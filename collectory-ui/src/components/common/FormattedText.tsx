import { formatText } from '../../utils/formatText';

interface FormattedTextProps {
  text?: string | null;
  className?: string;
}

export default function FormattedText({ text, className }: FormattedTextProps) {
  if (!text) return null;
  return <div className={className} dangerouslySetInnerHTML={{ __html: formatText(text) }} />;
}
