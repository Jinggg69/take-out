import { readClientEnvironment } from './environment'

export const clientEnvironment = readClientEnvironment(import.meta.env)
