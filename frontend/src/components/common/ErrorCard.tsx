import { useEffect, useState } from 'react';
import { XCircle, AlertTriangle, Info, CheckCircle, X, RotateCcw, ChevronDown, ChevronUp } from 'lucide-react';

export type ErrorSeverity = 'error' | 'warning' | 'info' | 'success';

export interface ErrorCardProps {
  isVisible: boolean;
  severity?: ErrorSeverity;
  title: string;
  description?: string;
  details?: string;
  onClose?: () => void;
  onRetry?: () => void;
  onViewDetails?: () => void;
  showCloseButton?: boolean;
  showRetryButton?: boolean;
  showDetailsButton?: boolean;
  autoDismiss?: boolean;
  autoDismissDelay?: number;
  className?: string;
  position?: 'top' | 'bottom' | 'center';
}

const severityConfig = {
  error: {
    icon: XCircle,
    bgColor: 'bg-red-500/8',
    borderColor: 'border-red-500/40',
    iconBg: 'bg-red-500/20',
    iconColor: 'text-red-400',
    titleColor: 'text-red-300',
    buttonColor: 'bg-red-500/20 hover:bg-red-500/30 text-red-300',
    glowColor: 'shadow-red-500/25',
    gradientStart: 'from-red-500/10',
    gradientEnd: 'to-transparent',
  },
  warning: {
    icon: AlertTriangle,
    bgColor: 'bg-amber-500/8',
    borderColor: 'border-amber-500/40',
    iconBg: 'bg-amber-500/20',
    iconColor: 'text-amber-400',
    titleColor: 'text-amber-300',
    buttonColor: 'bg-amber-500/20 hover:bg-amber-500/30 text-amber-300',
    glowColor: 'shadow-amber-500/25',
    gradientStart: 'from-amber-500/10',
    gradientEnd: 'to-transparent',
  },
  info: {
    icon: Info,
    bgColor: 'bg-blue-500/8',
    borderColor: 'border-blue-500/40',
    iconBg: 'bg-blue-500/20',
    iconColor: 'text-blue-400',
    titleColor: 'text-blue-300',
    buttonColor: 'bg-blue-500/20 hover:bg-blue-500/30 text-blue-300',
    glowColor: 'shadow-blue-500/25',
    gradientStart: 'from-blue-500/10',
    gradientEnd: 'to-transparent',
  },
  success: {
    icon: CheckCircle,
    bgColor: 'bg-green-500/8',
    borderColor: 'border-green-500/40',
    iconBg: 'bg-green-500/20',
    iconColor: 'text-green-400',
    titleColor: 'text-green-300',
    buttonColor: 'bg-green-500/20 hover:bg-green-500/30 text-green-300',
    glowColor: 'shadow-green-500/25',
    gradientStart: 'from-green-500/10',
    gradientEnd: 'to-transparent',
  },
};

export function ErrorCard({
  isVisible,
  severity = 'error',
  title,
  description,
  details,
  onClose,
  onRetry,
  onViewDetails,
  showCloseButton = true,
  showRetryButton = false,
  showDetailsButton = !!details,
  autoDismiss = false,
  autoDismissDelay = 5000,
  className = '',
  position = 'top',
}: ErrorCardProps) {
  const [isShowing, setIsShowing] = useState(false);
  const [isExiting, setIsExiting] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  const config = severityConfig[severity];
  const IconComponent = config.icon;

  useEffect(() => {
    if (isVisible && !isShowing) {
      setIsShowing(true);
    } else if (!isVisible && isShowing && !isExiting) {
      setIsExiting(true);
      setTimeout(() => {
        setIsShowing(false);
        setIsExiting(false);
      }, 350);
    }
  }, [isVisible, isShowing, isExiting]);

  useEffect(() => {
    if (isVisible && autoDismiss && onClose) {
      const timer = setTimeout(() => {
        onClose();
      }, autoDismissDelay);
      return () => clearTimeout(timer);
    }
  }, [isVisible, autoDismiss, autoDismissDelay, onClose]);

  if (!isShowing) return null;

  const positionClasses = {
    top: 'top-4 left-1/2 -translate-x-1/2',
    bottom: 'bottom-4 left-1/2 -translate-x-1/2',
    center: 'top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2',
  };

  return (
    <div
      className={`
        fixed z-50 ${positionClasses[position]}
        max-w-md w-[calc(100%-2rem)]
        transition-all duration-350 ease-out
        ${isExiting ? 'opacity-0 translate-y-4 scale-95' : 'opacity-100 translate-y-0 scale-100'}
        ${className}
      `}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <div className={`
        relative rounded-2xl border ${config.borderColor} ${config.bgColor}
        backdrop-blur-xl bg-white/5
        p-5
        shadow-2xl ${config.glowColor}
        transition-all duration-300 ease-out
        ${isHovered ? 'transform -translate-y-1 shadow-3xl' : 'transform translate-y-0'}
      `}>
        <div className={`
          absolute inset-0 rounded-2xl
          bg-gradient-to-br ${config.gradientStart} ${config.gradientEnd}
          pointer-events-none
        `} />

        <div className={`
          absolute -inset-px rounded-2xl
          bg-gradient-to-r ${config.iconColor}10 to-transparent
          pointer-events-none
        `} />

        <div className="flex items-start gap-4 relative z-10">
          <div className={`
            flex-shrink-0 w-12 h-12
            rounded-full ${config.iconBg}
            flex items-center justify-center
            animate-bounce-in
            shadow-lg ${config.glowColor}
          `}>
            <IconComponent className={`w-6 h-6 ${config.iconColor}`} />
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-start justify-between gap-2">
              <h3 className={`font-semibold text-base ${config.titleColor} leading-tight`}>
                {title}
              </h3>

              {showCloseButton && onClose && (
                <button
                  onClick={onClose}
                  className="flex-shrink-0 p-1.5 rounded-lg hover:bg-white/10 transition-all hover:scale-110"
                  aria-label="关闭"
                >
                  <X className="w-4 h-4 text-gray-400 hover:text-gray-200" />
                </button>
              )}
            </div>

            {description && (
              <p className="mt-2 text-sm text-gray-300 leading-relaxed">
                {description}
              </p>
            )}

            {(showDetailsButton || showDetails) && details && (
              <div className="mt-3">
                <button
                  onClick={() => setShowDetails(!showDetails)}
                  className={`
                    flex items-center gap-2 text-sm ${config.buttonColor}
                    px-3 py-1.5 rounded-lg transition-all hover:scale-105
                  `}
                >
                  {showDetails ? (
                    <>
                      <ChevronUp className="w-4 h-4" />
                      隐藏详情
                    </>
                  ) : (
                    <>
                      <ChevronDown className="w-4 h-4" />
                      查看详情
                    </>
                  )}
                </button>

                {showDetails && (
                  <div className="mt-3 p-3 bg-black/30 rounded-xl border border-gray-700/50 animate-slide-down">
                    <pre className="text-xs text-gray-400 whitespace-pre-wrap max-h-32 overflow-y-auto">
                      {details}
                    </pre>
                  </div>
                )}
              </div>
            )}

            {(showRetryButton || onRetry) && (
              <div className="mt-4 flex items-center gap-2">
                <button
                  onClick={onRetry}
                  className={`
                    flex items-center gap-2 text-sm font-medium
                    px-4 py-2 rounded-xl transition-all
                    ${config.buttonColor}
                    hover:shadow-lg hover:shadow-current/30
                    hover:scale-105 active:scale-95
                  `}
                >
                  <RotateCcw className="w-4 h-4" />
                  重试
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="absolute bottom-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-white/20 to-transparent" />
      </div>
    </div>
  );
}

export default ErrorCard;