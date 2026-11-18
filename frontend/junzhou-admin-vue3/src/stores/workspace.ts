import { ref, computed } from 'vue'
export interface AccessibleStore { id: string; name: string; permissions: string[] }
export interface MerchantIdentity { tenantName: string; roleNames: string[] }
export function createWorkspace() {
 const stores=ref<AccessibleStore[]>([]), storeId=ref<string|null>(null), revision=ref(0)
 const identity=ref<MerchantIdentity>({tenantName:'',roleNames:[]})
 const tenantPermissions=ref<string[]>([])
 const current=computed(()=>stores.value.find(s=>s.id===storeId.value))
 function select(id:string){if(!stores.value.some(s=>s.id===id))throw new Error('门店未授权');if(id!==storeId.value){revision.value++;storeId.value=id}}
 function load(rows:AccessibleStore[], merchant?:MerchantIdentity, tenantActions:string[]=[]){identity.value={tenantName:merchant?.tenantName??'',roleNames:[...(merchant?.roleNames??[])]};tenantPermissions.value=[...tenantActions];stores.value=rows.map(s=>({...s,id:String(s.id)}));revision.value++;storeId.value=stores.value[0]?.id??null}
 function reset(){identity.value={tenantName:'',roleNames:[]};tenantPermissions.value=[];revision.value++;storeId.value=null;stores.value=[]}
 const can=(action:string)=>current.value?.permissions.includes(action)??false
 const canTenant=(action:string)=>can(action)&&tenantPermissions.value.includes(action)
 return { identity,stores,storeId,revision,current,tenantPermissions,select,load,reset,can,canTenant }
}
export const pendingWrites=ref(0)
export const workspace=createWorkspace()
export const pagePermissions:Record<string,string>={dining:'dining:read',dashboard:'workspace:read',statistics:'report:read',order:'order:read',dish:'dish:read',setmeal:'setmeal:read',category:'category:read',employee:'employee:read',access:'employee:manage',store:'shop:read',decoration:'decoration:manage',points:'member:read'}
export const memberPagePermissions:Record<string,string>={'/member/list':'member:read','/member/levels':'member:manage','/member/tags':'member:manage','/member/recharge-plans':'member:read','/member/coupons':'coupon:read','/member/settings':'member:manage'}
export function canVisit(path:string){
 const pathname=path.split(/[?#]/,1)[0]??''
 if(pathname==='/points')return workspace.canTenant('member:read')
 if(pathname.startsWith('/member/'))return workspace.can(memberPagePermissions[pathname]??'')
 const section=pathname.split('/')[1]??''
 const action=pathname.includes('/add')?section+':'+(path.includes('id=')?'update':'create'):pagePermissions[section]
 return !!action && workspace.can(action)
}
