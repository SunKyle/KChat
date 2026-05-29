import * as Icons from 'lucide-react';
import { createContext, useContext, forwardRef } from 'react';

export interface IconTheme {
  size: number;
  strokeWidth: number;
  color: string;
}

export interface IconProviderProps {
  theme?: Partial<IconTheme>;
  children: React.ReactNode;
}

const defaultTheme: IconTheme = {
  size: 20,
  strokeWidth: 2,
  color: 'currentColor',
};

export const IconThemeContext = createContext<IconTheme>(defaultTheme);

export function IconProvider({ theme, children }: IconProviderProps) {
  const mergedTheme = { ...defaultTheme, ...theme };
  return (
    <IconThemeContext.Provider value={mergedTheme}>
      {children}
    </IconThemeContext.Provider>
  );
}

export type IconName = keyof typeof Icons;

export interface IconProps extends React.SVGProps<SVGSVGElement> {
  name: IconName;
  size?: number;
  strokeWidth?: number;
}

export const Icon = forwardRef<SVGSVGElement, IconProps>(
  ({ name, size, strokeWidth, className, style, ...props }, ref) => {
    const theme = useContext(IconThemeContext);
    const IconComponent = Icons[name];
    
    if (!IconComponent) {
      console.warn(`Icon "${name}" not found in lucide-react`);
      return null;
    }

    const computedSize = size ?? theme.size;
    const computedStrokeWidth = strokeWidth ?? theme.strokeWidth;

    return (
      <IconComponent
        ref={ref}
        size={computedSize}
        strokeWidth={computedStrokeWidth}
        className={className}
        style={{
          color: theme.color,
          ...style,
        }}
        {...props}
      />
    );
  }
);

Icon.displayName = 'Icon';

export { Icons };
