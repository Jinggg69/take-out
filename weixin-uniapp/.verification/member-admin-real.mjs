import { chromium } from 'playwright'
import fs from 'node:fs/promises'
import assert from 'node:assert/strict'
const state=JSON.parse(await fs.readFile('D:/take-out/local-reports/member-http-browser-state.json','utf8'))
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'})
const context=await browser.newContext({viewport:{width:1440,height:960}})
await context.addCookies([{name:'token',value:state.adminToken,domain:'127.0.0.1',path:'/'},{name:'username',value:'junzhou',domain:'127.0.0.1',path:'/'},{name:'user_info',value:encodeURIComponent(JSON.stringify({id:state.adminId,name:'商户管理员',userName:'junzhou',accountType:'EMPLOYEE',token:state.adminToken})),domain:'127.0.0.1',path:'/'}])
const page=await context.newPage();const errors=[];const bad=[]
page.on('pageerror',e=>errors.push(e.message))
await page.routeWebSocket(/\/ws\//, socket => { socket.connectToServer(socket.url().replace(/\/\/[^/]+/,'//127.0.0.1:18081')) })
await page.route(/^http:\/\/127\.0\.0\.1:8889\/api\//,async route=>{
 const r=route.request(),u=new URL(r.url());const headers={...r.headers()};delete headers.host
 const response=await context.request.fetch(state.origin+u.pathname.replace(/^\/api/,'/admin')+u.search,{method:r.method(),headers,data:r.postDataBuffer()||undefined})
 if(response.status()>=400)bad.push({path:u.pathname,status:response.status()})
 await route.fulfill({response})
})
try {
 await page.goto('http://127.0.0.1:8889/#/member/list')
 await page.getByText('Acceptance member',{exact:true}).waitFor({timeout:10000})
 assert.equal(await page.getByRole('button',{name:'会员管理',exact:true}).getAttribute('aria-expanded'),'true')
 await page.screenshot({path:'D:/take-out/local-reports/member-real-desktop.png',fullPage:true})
 await page.getByRole('button',{name:'详情',exact:true}).first().click()
 await page.getByRole('tab',{name:'积分流水'}).waitFor({timeout:10000})
 await page.screenshot({path:'D:/take-out/local-reports/member-real-detail.png',fullPage:true})
 for(const route of ['levels','tags','points','recharge-plans','coupons','settings']) {
  await page.goto('http://127.0.0.1:8889/#/member/'+route)
  await page.locator('.member-page').waitFor({timeout:10000})
  await page.waitForTimeout(120)
  assert.ok(!(await page.locator('body').innerText()).includes('加载失败'),route)
 }
 await page.setViewportSize({width:390,height:844})
 await page.goto('http://127.0.0.1:8889/#/member/list')
 await page.getByText('Acceptance member',{exact:true}).waitFor({timeout:10000})
 const size=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:window.innerWidth}))
 assert.ok(size.scroll<=size.width+1,JSON.stringify(size))
 await page.screenshot({path:'D:/take-out/local-reports/member-real-mobile.png',fullPage:true})
 assert.deepEqual(errors,[]);assert.deepEqual(bad,[])
 console.log('PASS actual-API admin membership desktop/detail/mobile and seven routes; no runtime/API errors or page overflow')
} catch(error) { console.log('DEBUG',JSON.stringify({url:page.url(),body:(await page.locator('body').innerText()).slice(0,3000),errors,bad}));await page.screenshot({path:'D:/take-out/local-reports/member-browser-error.png',fullPage:true});throw error } finally {await browser.close()}
