export function isImageModel(modelId: string): boolean {
  const lower = modelId.toLowerCase()
  return (
    lower.includes('dall-e') ||
    lower.includes('image') ||
    lower.includes('sdxl') ||
    lower.includes('stable-diffusion')
  )
}
