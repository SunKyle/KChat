import { Eye, EyeOff, MessageSquare, CheckCircle2, Lock, Shield } from 'lucide-react'
import { useUser } from '../../context/UserContext'

interface ToggleProps {
  enabled: boolean
  onChange: (value: boolean) => void
  disabled?: boolean
}

function Toggle({ enabled, onChange, disabled }: ToggleProps) {
  return (
    <button
      onClick={() => !disabled && onChange(!enabled)}
      disabled={disabled}
      className={`relative w-11 h-6 rounded-full transition-colors ${
        enabled ? 'bg-sky-500' : 'theme-bg-hover'
      } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
    >
      <span
        className={`absolute top-0.5 left-0.5 w-5 h-5 bg-[var(--bg-card)] rounded-full shadow transition-transform ${
          enabled ? 'translate-x-5' : 'translate-x-0'
        }`}
      />
    </button>
  )
}

export function Privacy() {
  const { profile, updatePrivacy, isLoading } = useUser()

  const handlePrivacyChange = async (
    key: keyof NonNullable<typeof profile>['privacy'],
    value: boolean
  ) => {
    if (!profile) return
    try {
      await updatePrivacy({
        [key]: value,
      })
    } catch (err) {
      console.error('Failed to update privacy:', err)
    }
  }

  if (!profile) return null

  const privacyOptions = [
    {
      key: 'onlineStatus' as const,
      label: '在线状态',
      description: '向其他人显示您的在线状态',
      icon: Eye,
    },
    {
      key: 'readReceipts' as const,
      label: '已读回执',
      description: '让对方知道您已阅读消息',
      icon: CheckCircle2,
    },
    {
      key: 'typingIndicator' as const,
      label: '输入状态',
      description: '向对方显示您正在输入',
      icon: MessageSquare,
    },
    {
      key: 'messageHistory' as const,
      label: '消息历史',
      description: '保存您的聊天记录',
      icon: EyeOff,
    },
  ]

  return (
    <div className='space-y-6'>
      <div className='card-float-solid rounded-2xl p-6'>
        <div className='flex items-center gap-2 mb-4'>
          <Lock className='w-[18px] h-[18px] theme-text-muted' />
          <h3 className='font-medium theme-text-primary'>隐私设置</h3>
        </div>

        <div className='space-y-4'>
          {privacyOptions.map((option) => (
            <div key={option.key} className='flex items-center justify-between'>
              <div className='flex items-center gap-3'>
                <div className='w-8 h-8 rounded-lg theme-bg-hover flex items-center justify-center'>
                  <option.icon className='w-4 h-4 theme-text-muted' />
                </div>
                <div>
                  <div className='text-sm font-medium theme-text-primary'>{option.label}</div>
                  <div className='text-xs theme-text-muted'>{option.description}</div>
                </div>
              </div>
              <Toggle
                enabled={profile.privacy[option.key]}
                onChange={(value) => handlePrivacyChange(option.key, value)}
                disabled={isLoading}
              />
            </div>
          ))}
        </div>
      </div>

      <div className='card-float-solid rounded-2xl p-6'>
        <div className='flex items-center gap-2 mb-4'>
          <Shield className='w-[18px] h-[18px] theme-text-muted' />
          <h3 className='font-medium theme-text-primary'>数据安全</h3>
        </div>

        <div className='space-y-3'>
          <div className='flex items-start gap-3 p-3 rounded-lg theme-bg-hover/50'>
            <div className='w-2 h-2 rounded-full bg-amber-400 mt-2 flex-shrink-0' />
            <div>
              <div className='text-sm font-medium theme-text-primary'>数据加密</div>
              <div className='text-xs theme-text-muted'>您的所有数据都采用端到端加密传输和存储</div>
            </div>
          </div>

          <div className='flex items-start gap-3 p-3 rounded-lg theme-bg-hover/50'>
            <div className='w-2 h-2 rounded-full bg-green-400 mt-2 flex-shrink-0' />
            <div>
              <div className='text-sm font-medium theme-text-primary'>安全审计</div>
              <div className='text-xs theme-text-muted'>定期进行安全审计，确保数据保护措施有效</div>
            </div>
          </div>

          <div className='flex items-start gap-3 p-3 rounded-lg theme-bg-hover/50'>
            <div className='w-2 h-2 rounded-full bg-blue-400 mt-2 flex-shrink-0' />
            <div>
              <div className='text-sm font-medium theme-text-primary'>隐私政策</div>
              <div className='text-xs theme-text-muted'>我们承诺不会出售或分享您的个人数据</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
