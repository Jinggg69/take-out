import { ref, computed } from 'vue'
export interface AccessibleStore { id: string; name: string; permissions: string[] }
export function createWorkspace() {
 const stores=ref<AccessibleStore[]>([]), storeId=ref<string|null>(null), revision=ref(0)
 const current=computed(()=>stores.value.find(s=>s.id===storeId.value))
 function select(id:string){if(!stores.value.some(s=>s.id===id))throw new Error('门店未授权');if(id!==storeId.value){revision.value++;storeId.value=id}}
 function load(rows:AccessibleStore[]){stores.value=rows.map(s=>({...s,id:String(s.id)}));revision.value++;storeId.value=stores.value[0]?.id??null}
 function reset(){revision.value++;storeId.value=null;stores.value=[]}
 const can=(action:string)=>current.value?.permissions.includes(action)??false
 return { stores,storeId,revision,current,select,load,reset,can }
}
export const pendingWrites=ref(0)
export const workspace=createWorkspace()
export const pagePermissions:Record<string,string>={dashboard:'workspace:read',statistics:'report:read',order:'order:read',dish:'dish:read',setmeal:'setmeal:read',category:'category:read',employee:'employee:read',access:'employee:manage'}
export function canVisit(path:string){const section=path.split('/')[1]??'';const action=path.includes('/add')?section+':'+(path.includes('id=')?'update':'create'):pagePermissions[section];return !!action && workspace.can(action)}
