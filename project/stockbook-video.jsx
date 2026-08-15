/* Stockbook — product walkthrough video.
   Colors are Nocturne token values written as literals so the exported SVG
   resolves them without the external stylesheet. */

const BG = '#161826', SURF = '#232532', TX = '#e9e9ed';
const ACC = '#9184d9', A4 = '#b5abfc', A3 = '#d2cefd', A7 = '#5d5294', A9 = '#2b2741';
const N8 = '#3f424d', N6 = '#75798c', N5 = '#9397ab', N4 = '#b2b6ca';
const FONT = 'Inter, system-ui, sans-serif';
const W = 402, H = 874;

/* ── the three motion helpers ─────────────────────────────────────── */
const MOTION = {
  enter: (T, a, b) => animate({ from: 0, to: 1, start: a, end: b, ease: Easing.easeOutCubic })(T),
  pop: (T, a) => {
    const p = clamp((T - a) / 0.45, 0, 1);
    return Math.sin(p * Math.PI) * (1 - p * 0.4);
  },
  glide: (T, pts) => {
    if (T <= pts[0][0]) return pts[0][1];
    for (let i = 0; i < pts.length - 1; i++) {
      const [t0, v0] = pts[i], [t1, v1] = pts[i + 1];
      if (T <= t1) return animate({ from: v0, to: v1, start: t0, end: t1, ease: Easing.easeInOutCubic })(T);
    }
    return pts[pts.length - 1][1];
  }
};
const typed = (s, T, a, b) => s.slice(0, Math.round(clamp((T - a) / (b - a), 0, 1) * s.length));
const money = n => 'SAR ' + (Number.isInteger(n) ? n : n.toFixed(2));

/* ── phone primitives ─────────────────────────────────────────────── */
function Sc({ children, pad }) {
  return <div style={{ position: 'absolute', inset: 0, background: BG, color: TX, fontFamily: FONT, display: 'flex', flexDirection: 'column', padding: pad || 0, boxSizing: 'border-box' }}>{children}</div>;
}
function Status({ offline }) {
  return (
    <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 54, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 30px', fontSize: 15, fontWeight: 600, color: TX, zIndex: 4 }}>
      <span>9:41</span>
      <span style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12, color: offline ? A4 : TX }}>
        {offline ? '✈' : '▮▮▮'}<span style={{ fontSize: 11 }}>{offline ? 'offline' : ''}</span>
      </span>
    </div>
  );
}
function Btn({ children, primary, wide, dim, h, style }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
      height: h || 48, borderRadius: 8, fontSize: 15, fontWeight: 500,
      border: '1px solid ' + (dim ? N8 : primary ? ACC : N8),
      color: dim ? N6 : primary ? ACC : TX, width: wide ? '100%' : undefined,
      padding: wide ? 0 : '0 16px', boxSizing: 'border-box', ...style
    }}>{children}</div>
  );
}
function Field({ label, value, ph, accent, h }) {
  return (
    <div style={{ flex: 1 }}>
      {label && <div style={{ fontSize: 12, color: 'rgba(233,233,237,.7)', marginBottom: 5 }}>{label}</div>}
      <div style={{ minHeight: h || 44, background: SURF, border: '1px solid ' + (accent || N8), borderRadius: 8, padding: '0 10px', display: 'flex', alignItems: 'center', fontSize: 14.5, color: value ? TX : N6 }}>{value || ph}</div>
    </div>
  );
}
function Head({ kicker, title, right }) {
  return (
    <div style={{ padding: '58px 20px 12px', display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between' }}>
      <div>
        {kicker && <div style={{ fontSize: 10.5, letterSpacing: '.1em', textTransform: 'uppercase', color: ACC }}>{kicker}</div>}
        <div style={{ fontSize: 25, marginTop: 3, letterSpacing: '-.02em' }}>{title}</div>
      </div>
      {right && <div style={{ fontSize: 12.5, color: ACC }}>{right}</div>}
    </div>
  );
}
function Row({ name, sub, right, rightSub, bare }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, background: bare ? 'transparent' : SURF, borderRadius: 9, padding: 12 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14.5 }}>{name}</div>
        {sub && <div style={{ fontSize: 11.5, color: N5, marginTop: 2 }}>{sub}</div>}
      </div>
      {right !== undefined && <div style={{ textAlign: 'right' }}>
        <div style={{ fontSize: 15 }}>{right}</div>
        {rightSub && <div style={{ fontSize: 11.5, color: N5, marginTop: 2 }}>{rightSub}</div>}
      </div>}
    </div>
  );
}
function Tabs({ active }) {
  const items = [['Today', '⌂'], ['Items', '◫'], ['Sell', '＋'], ['Bills', '☰']];
  return (
    <div style={{ marginTop: 'auto', display: 'flex', padding: '6px 8px 24px', background: SURF, boxShadow: '0 -1px 0 ' + N8 }}>
      {items.map(([l, g]) => (
        <div key={l} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3, padding: '7px 0', color: l === active ? ACC : N5 }}>
          <div style={{ fontSize: 19, lineHeight: 1 }}>{g}</div>
          <div style={{ fontSize: 10.5 }}>{l}</div>
        </div>
      ))}
    </div>
  );
}
function Steps({ n }) {
  return (
    <div style={{ display: 'flex', gap: 6, margin: '0 0 18px' }}>
      {[0, 1, 2].map(i => <div key={i} style={{ flex: 1, height: 3, borderRadius: 2, background: i <= n ? ACC : N8 }} />)}
    </div>
  );
}
function Cursor({ x, y, tap }) {
  return (
    <div style={{ position: 'absolute', left: x, top: y, zIndex: 9, pointerEvents: 'none' }}>
      {tap > 0 && <div style={{ position: 'absolute', left: -24, top: -24, width: 48, height: 48, borderRadius: 24, border: '2px solid ' + A4, opacity: (1 - tap) * 0.9, transform: 'scale(' + (0.4 + tap * 1.5) + ')' }} />}
      <svg width="30" height="35" viewBox="0 0 22 26" style={{ filter: 'drop-shadow(0 3px 7px rgba(0,0,0,.7))', transform: 'scale(' + (1 - tap * 0.18) + ')' }}>
        <path d="M2 1 L2 20 L7 15.5 L10.5 23 L14 21.5 L10.5 14.5 L17 14 Z" fill="#fff" stroke="#161826" strokeWidth="1.4" strokeLinejoin="round" />
      </svg>
    </div>
  );
}

/* ── screens ──────────────────────────────────────────────────────── */
function ScWelcome({ T, c, name }) {
  const on = typed(name, T, c + 0.9, c + 2.4).length > 0;
  return (
    <Sc pad="54px 20px 0">
      <Steps n={0} />
      <div style={{ width: 38, height: 38, borderRadius: 10, border: '1px solid ' + ACC, color: ACC, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 19, marginBottom: 14 }}>◫</div>
      <div style={{ fontSize: 26, letterSpacing: '-.02em', marginBottom: 5 }}>Welcome to Stockbook</div>
      <div style={{ fontSize: 13, color: N5, lineHeight: 1.5, marginBottom: 18 }}>Everything stays on this phone — no account, no signal needed. First, what should we call you?</div>
      <Field label="Your name" value={typed(name, T, c + 0.9, c + 2.4)} ph="Business owner name" accent={on ? N8 : ACC} h={46} />
      <div style={{ marginTop: 'auto', paddingBottom: 40 }}><Btn wide primary={on} dim={!on}>Continue</Btn></div>
    </Sc>
  );
}
const CAPS = ['Lever Handle Lock', 'Cisa lock', 'Padlock', 'Deadbolt'];
function ScProducts({ T, c, name }) {
  const added = CAPS.filter((_, i) => T > c + 1.1 + i * 0.75);
  return (
    <Sc pad="54px 20px 0">
      <Steps n={1} />
      <div style={{ fontSize: 11, letterSpacing: '.09em', textTransform: 'uppercase', color: ACC, marginBottom: 6 }}>Hello, {name.split(' ')[0]}</div>
      <div style={{ fontSize: 26, letterSpacing: '-.02em', marginBottom: 5 }}>What do you stock?</div>
      <div style={{ fontSize: 13, color: N5, lineHeight: 1.5, marginBottom: 16 }}>Names only for now. Prices and counts come next.</div>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <Field ph="e.g. 4 inch hinge" h={46} />
        <div style={{ width: 52 }}><Btn primary h={46} style={{ width: 52, padding: 0 }}>＋</Btn></div>
      </div>
      <div style={{ fontSize: 10.5, letterSpacing: '.09em', textTransform: 'uppercase', color: N5, marginBottom: 8 }}>Common hardware lines</div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 20 }}>
        {CAPS.map((s, i) => {
          const gone = added.includes(s);
          return <div key={s} style={{ fontSize: 11.5, padding: '6px 10px', borderRadius: 6, border: '1px solid ' + ACC, color: ACC, opacity: gone ? 0.25 : 1, transform: 'scale(' + (1 + MOTION.pop(T, c + 1.05 + i * 0.75) * 0.12) + ')' }}>+ {s}</div>;
        })}
      </div>
      <div style={{ fontSize: 10.5, letterSpacing: '.09em', textTransform: 'uppercase', color: N5, marginBottom: 8 }}>{added.length ? 'Added · ' + added.length : 'Nothing added yet'}</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {added.map((s, i) => (
          <div key={s} style={{ display: 'flex', alignItems: 'center', background: SURF, borderRadius: 8, padding: '10px 13px', fontSize: 14.5, opacity: MOTION.enter(T, c + 1.1 + i * 0.75, c + 1.4 + i * 0.75), transform: 'translateY(' + (1 - MOTION.enter(T, c + 1.1 + i * 0.75, c + 1.45 + i * 0.75)) * 10 + 'px)' }}>
            <span style={{ flex: 1 }}>{s}</span><span style={{ color: N6 }}>✕</span>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 'auto', paddingBottom: 40, display: 'flex', gap: 8 }}>
        <Btn style={{ width: 56 }}>←</Btn>
        <Btn wide primary={added.length > 0} dim={!added.length}>Next — stock &amp; prices</Btn>
      </div>
    </Sc>
  );
}
const CATALOG = [
  { n: 'Lever Handle Lock', s: 40, c: 55, p: 85 },
  { n: 'Cisa lock', s: 25, c: 95, p: 145 },
  { n: 'Padlock', s: 60, c: 18, p: 32 },
  { n: 'Deadbolt', s: 30, c: 62, p: 95 }
];
function ScPrices({ T, c }) {
  const done = CATALOG.map((_, i) => T > c + 0.8 + i * 0.85);
  const left = done.filter(d => !d).length;
  return (
    <Sc pad="54px 20px 0">
      <Steps n={2} />
      <div style={{ fontSize: 26, letterSpacing: '-.02em', marginBottom: 5 }}>Stock and prices</div>
      <div style={{ fontSize: 13, color: N5, lineHeight: 1.5, marginBottom: 16 }}>All three are needed for every item — the count on the shelf, what you paid, what you charge.</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {CATALOG.map((it, i) => (
          <div key={it.n} style={{ background: SURF, borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 14.5, marginBottom: 10 }}>{it.n}</div>
            <div style={{ display: 'flex', gap: 8 }}>
              <Field label="In stock" value={done[i] ? String(it.s) : ''} accent={done[i] ? N8 : ACC} h={42} />
              <Field label="You pay" value={done[i] ? String(it.c) : ''} accent={done[i] ? N8 : ACC} h={42} />
              <Field label="You sell" value={done[i] ? String(it.p) : ''} accent={done[i] ? A7 : ACC} h={42} />
            </div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 'auto', paddingBottom: 40 }}>
        <div style={{ fontSize: 11.5, color: left ? N5 : A4, textAlign: 'center', marginBottom: 8 }}>{left ? left + ' items still need stock, buying and selling price.' : 'All set — stock and both prices filled in.'}</div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Btn style={{ width: 56 }}>←</Btn>
          <Btn wide primary={!left} dim={!!left}>Open the shop</Btn>
        </div>
      </div>
    </Sc>
  );
}
function ScDash({ T, c, name, sales, bills, due, backed }) {
  return (
    <Sc>
      <Head kicker="Tuesday, 11 August" title={'Hello, ' + name.split(' ')[0]} right="Start over" />
      <div style={{ padding: '4px 20px 18px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 9, marginBottom: 9 }}>
          <div style={{ background: 'linear-gradient(155deg,' + A9 + ',' + SURF + ')', borderRadius: 12, padding: 14, boxShadow: '0 0 0 1px ' + N8 }}>
            <div style={{ fontSize: 11, color: N5, marginBottom: 3 }}>Sold today</div>
            <div style={{ fontSize: 26, letterSpacing: '-.025em' }}>{money(sales)}</div>
          </div>
          <div style={{ background: SURF, borderRadius: 12, padding: 14, boxShadow: '0 0 0 1px ' + N8 }}>
            <div style={{ fontSize: 11, color: N5, marginBottom: 3 }}>Bills</div>
            <div style={{ fontSize: 26, letterSpacing: '-.025em' }}>{bills}</div>
          </div>
        </div>
        {due > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, background: SURF, borderLeft: '2px solid ' + ACC, borderRadius: '0 10px 10px 0', padding: '12px 13px', marginBottom: 18 }}>
            <div style={{ flex: 1, fontSize: 12.5, color: N4 }}>Ahmed Contracting still owes</div>
            <div style={{ fontSize: 16, color: A4 }}>{money(due)}</div>
          </div>
        )}
        <div style={{ fontSize: 10.5, letterSpacing: '.09em', textTransform: 'uppercase', color: N5, marginBottom: 9 }}>Recent bills</div>
        {bills === 0
          ? <div style={{ border: '1px dashed ' + N8, borderRadius: 10, padding: '22px 16px', textAlign: 'center' }}>
              <div style={{ fontSize: 13, color: N5, marginBottom: 10 }}>No bills yet today.</div>
              <div style={{ display: 'flex', justifyContent: 'center' }}><Btn primary h={38}>＋ Start a bill</Btn></div>
            </div>
          : <Row name="Padlock, Cisa lock" sub="Ahmed Contracting · 09:41 · 2 items · owes SAR 94" right={money(194)} />}
        <div style={{ marginTop: 18, display: 'flex', alignItems: 'center', gap: 11, padding: 12, border: '1px dashed ' + N8, borderRadius: 10 }}>
          <div style={{ fontSize: 19, color: backed ? ACC : N6 }}>◇</div>
          <div style={{ flex: 1, fontSize: 12, color: N5, lineHeight: 1.4 }}>{backed ? 'Backup written. Copy it somewhere safe — everything lives on this phone only.' : 'Nothing backed up yet. Everything lives on this phone only.'}</div>
          <Btn h={34} style={{ fontSize: 12 }}>Save file</Btn>
        </div>
      </div>
      <Tabs active="Today" />
    </Sc>
  );
}
function ScPicker({ T, c, query, list, cartN, cartTotal }) {
  return (
    <Sc>
      <Head title="New bill" right={cartN ? cartN + ' lines' : 'empty'} />
      <div style={{ padding: '0 20px 10px' }}><Field value={query} ph="Add a product…" h={42} accent={query ? ACC : N8} /></div>
      <div style={{ padding: '0 20px 8px', fontSize: 11.5, color: N5 }}>{query ? 'Matching “' + query + '”' : 'All 4 products — tap to add'}</div>
      <div style={{ padding: '0 20px', display: 'flex', flexDirection: 'column', gap: 5 }}>
        {list.map(it => (
          <div key={it.n} style={{ display: 'flex', alignItems: 'center', gap: 10, background: SURF, borderRadius: 8, padding: '11px 12px' }}>
            <span style={{ flex: 1, fontSize: 14 }}>{it.n}</span>
            <span style={{ fontSize: 11.5, color: N5 }}>{it.s} pc</span>
            <span style={{ fontSize: 14, color: A4 }}>{money(it.p)}</span>
          </div>
        ))}
      </div>
      {cartN > 0 && (
        <div style={{ marginTop: 'auto', padding: '10px 20px 38px', background: SURF, boxShadow: '0 -1px 0 ' + N8, display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11.5, color: N5 }}>{cartN} lines</div>
            <div style={{ fontSize: 19 }}>{money(cartTotal)}</div>
          </div>
          <Btn primary h={44}>Done adding</Btn>
        </div>
      )}
      {cartN === 0 && <Tabs active="Sell" />}
    </Sc>
  );
}
function CartLine({ name, qty, price, base, stock, edited }) {
  return (
    <div style={{ background: SURF, borderRadius: 10, padding: '11px 12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        <span style={{ flex: 1, fontSize: 14.5 }}>{name}</span>
        <span style={{ fontSize: 15 }}>{money(qty * price)}</span>
        <span style={{ color: N6, fontSize: 14 }}>🗑</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', background: BG, borderRadius: 8, border: '1px solid ' + N8, height: 34 }}>
          <div style={{ width: 34, textAlign: 'center', fontSize: 15 }}>−</div>
          <div style={{ width: 44, textAlign: 'center', fontSize: 14 }}>{qty}</div>
          <div style={{ width: 34, textAlign: 'center', fontSize: 15 }}>＋</div>
        </div>
        <span style={{ fontSize: 11.5, color: N5 }}>pieces · {stock} in stock</span>
        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, background: BG, border: '1px solid ' + (edited ? ACC : N8), borderRadius: 8, padding: '0 9px', height: 34 }}>
          <span style={{ fontSize: 12.5, color: N5 }}>SAR</span>
          <span style={{ fontSize: 14, color: edited ? A3 : TX, minWidth: 46, textAlign: 'right' }}>{price}</span>
        </div>
      </div>
      {edited && <div style={{ fontSize: 11, color: A4, marginTop: 8 }}>✎ Usual price {money(base)} — changed for this bill only</div>}
    </div>
  );
}
function ScCart({ T, c, price, edited, customer, hints, part, paid, total }) {
  return (
    <Sc>
      <Head title="New bill" right="2 lines" />
      <div style={{ padding: '0 20px 10px' }}><Field ph="Add a product…" h={42} /></div>
      <div style={{ padding: '0 20px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <CartLine name="Padlock" qty={2} price={32} base={32} stock={60} />
        <CartLine name="Cisa lock" qty={1} price={price} base={145} stock={25} edited={edited} />
        <Btn h={44} style={{ fontSize: 13.5 }}>⌕ Add another item</Btn>
      </div>
      <div style={{ marginTop: 'auto', padding: '12px 20px 38px', background: SURF, boxShadow: '0 -1px 0 ' + N8, position: 'relative' }}>
        {hints && (
          <div style={{ position: 'absolute', left: 20, right: 20, bottom: 258, background: SURF, border: '1px solid ' + N8, borderRadius: 8, boxShadow: '0 6px 18px rgba(0,0,0,.55)', overflow: 'hidden', zIndex: 6 }}>
            {[['Ahmed Contracting', 'owes SAR 40'], ['Al-Rashid site', '3 bills']].map(([n, m]) => (
              <div key={n} style={{ display: 'flex', gap: 8, padding: '10px 12px', borderBottom: '1px solid ' + N8, fontSize: 13.5 }}>
                <span style={{ color: N5 }}>☺</span><span style={{ flex: 1 }}>{n}</span><span style={{ fontSize: 11, color: N5 }}>{m}</span>
              </div>
            ))}
          </div>
        )}
        <div style={{ marginBottom: 10 }}><Field value={customer} ph="Customer name" accent={customer ? N8 : ACC} h={40} /></div>
        <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
          <Btn h={38} wide style={{ fontSize: 13, borderColor: part ? N8 : ACC, color: part ? N5 : ACC }}>Paid in full</Btn>
          <Btn h={38} wide style={{ fontSize: 13, borderColor: part ? ACC : N8, color: part ? ACC : N5 }}>Part payment</Btn>
        </div>
        {part && <div style={{ marginBottom: 10 }}><Field label="Paid now" value={paid} h={40} /></div>}
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 4 }}>
          <span style={{ fontSize: 13, color: N5 }}>Total</span>
          <span style={{ fontSize: 28, letterSpacing: '-.025em' }}>{money(total)}</span>
        </div>
        {part && <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 8 }}>
          <span style={{ fontSize: 12.5, color: N5 }}>Balance</span>
          <span style={{ fontSize: 15, color: A4 }}>{money(total - (Number(paid) || 0))}</span>
        </div>}
        <Btn wide primary={!!customer} dim={!customer}>{customer ? 'Save bill' : 'Enter a customer name'}</Btn>
      </div>
    </Sc>
  );
}
function ScReceipt({ T, c }) {
  return (
    <Sc pad="66px 20px 38px">
      <div style={{ display: 'flex', alignItems: 'center', gap: 11, marginBottom: 18 }}>
        <div style={{ width: 36, height: 36, borderRadius: 18, border: '1px solid ' + ACC, color: ACC, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 17, transform: 'scale(' + (1 + MOTION.pop(T, c + 0.15) * 0.25) + ')' }}>✓</div>
        <div>
          <div style={{ fontSize: 18 }}>Bill saved</div>
          <div style={{ fontSize: 11.5, color: N5 }}>Bill #1 · 09:41 · Ahmed Contracting</div>
        </div>
      </div>
      <div style={{ background: SURF, borderRadius: 12, padding: '14px 16px' }}>
        {[['Padlock', '2 × SAR 32', 64], ['Cisa lock', '1 × SAR 130', 130]].map(([n, q, t]) => (
          <div key={n} style={{ display: 'flex', alignItems: 'baseline', gap: 10, padding: '7px 0' }}>
            <span style={{ flex: 1, fontSize: 14 }}>{n}</span><span style={{ fontSize: 11.5, color: N5 }}>{q}</span><span style={{ fontSize: 14 }}>{money(t)}</span>
          </div>
        ))}
        <div style={{ height: 1, background: 'linear-gradient(to right,transparent,' + N8 + ' 24px,' + N8 + ' calc(100% - 24px),transparent)', margin: '9px 0' }} />
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 13, color: N5 }}>Total</span><span style={{ fontSize: 25 }}>{money(194)}</span>
        </div>
        <div style={{ fontSize: 12.5, color: A4, marginTop: 7 }}>Paid SAR 100 · Ahmed Contracting owes SAR 94</div>
      </div>
      <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
        <Btn wide h={46} style={{ fontSize: 14 }}>See bills</Btn>
        <Btn wide primary h={46} style={{ fontSize: 14 }}>Next customer</Btn>
      </div>
    </Sc>
  );
}
function ScItems({ T, c, padStock }) {
  const rows = [
    { n: 'Lever Handle Lock', s: 40, cost: 55, p: 85 },
    { n: 'Cisa lock', s: 24, cost: 95, p: 145 },
    { n: 'Padlock', s: padStock, cost: 18, p: 32 },
    { n: 'Deadbolt', s: 30, cost: 62, p: 95 }
  ];
  return (
    <Sc>
      <Head title="Items" right="＋ Add" />
      <div style={{ padding: '0 20px 4px', fontSize: 11.5, color: N5, marginTop: -8 }}>4 products · 1 running low</div>
      <div style={{ padding: '10px 20px 0' }}><Field ph="Search" h={42} /></div>
      <div style={{ padding: '10px 20px 0', display: 'flex', flexDirection: 'column', gap: 6 }}>
        {rows.map(r => <Row key={r.n} name={r.n} sub={'buy ' + money(r.cost) + ' · you make ' + money(r.p - r.cost)} right={money(r.p)} rightSub={r.s + ' pc'} />)}
      </div>
      <Tabs active="Items" />
    </Sc>
  );
}
function ScRestock({ T, c, qty, cost, supplier }) {
  const t = (Number(qty) || 0) * (Number(cost) || 0);
  return (
    <Sc>
      <ScItems T={T} c={c} padStock={58} />
      <div style={{ position: 'absolute', inset: 0, background: 'rgba(16,17,28,.74)' }} />
      <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, background: SURF, borderRadius: '18px 18px 0 0', boxShadow: '0 -16px 40px rgba(0,0,0,.65)', padding: '10px 20px 32px', transform: 'translateY(' + (1 - MOTION.enter(T, c + 0.1, c + 0.7)) * 320 + 'px)' }}>
        <div style={{ width: 38, height: 4, borderRadius: 2, background: N8, margin: '0 auto 14px' }} />
        <div style={{ fontSize: 19, marginBottom: 3 }}>Add stock</div>
        <div style={{ fontSize: 12.5, color: N5, marginBottom: 14 }}>Padlock — 58 pieces on the shelf now</div>
        <div style={{ display: 'flex', gap: 5, marginBottom: 14 }}>
          <Btn h={34} wide style={{ fontSize: 12.5, borderColor: N8, color: N5 }}>Quick add</Btn>
          <Btn h={34} wide style={{ fontSize: 12.5, borderColor: ACC, color: ACC }}>Purchase entry</Btn>
        </div>
        <div style={{ marginBottom: 10 }}><Field label="Supplier" value={supplier} ph="Who delivered it" h={44} /></div>
        <div style={{ display: 'flex', gap: 10, marginBottom: 12 }}>
          <Field label="How many" value={qty} h={44} />
          <Field label="Paid per piece" value={cost} h={44} />
        </div>
        <div style={{ fontSize: 12, color: N5, marginBottom: 14, lineHeight: 1.5 }}>Bill total {money(t)}. This becomes the buying price used from now on.</div>
        <Btn wide primary h={46}>Record purchase</Btn>
      </div>
    </Sc>
  );
}

/* ── right-hand chapter panel ─────────────────────────────────────── */
const CHAPTERS = [
  ['Setup', 'Welcome'], ['Sell', 'Billing'], ['Prices', 'PriceEdit'],
  ['Customers', 'Customer'], ['Stock', 'Stock'], ['Offline', 'Offline']
];
function Panel({ T, CUES, title, line, from, to, i }) {
  const inn = MOTION.enter(T, from, from + 0.6), out = 1 - MOTION.enter(T, to - 0.4, to);
  const o = Math.min(inn, out);
  return (
    <div style={{ position: 'absolute', left: 0, top: 0, width: 780, opacity: o, transform: 'translateY(' + (1 - inn) * 26 + 'px)' }}>
      <div style={{ fontSize: 13, letterSpacing: '.14em', textTransform: 'uppercase', color: ACC, marginBottom: 14 }}>{String(i).padStart(2, '0')}</div>
      <div style={{ fontSize: 62, lineHeight: 1.04, letterSpacing: '-.03em', color: TX, marginBottom: 18, textWrap: 'pretty' }}>{title}</div>
      <div style={{ fontSize: 22, lineHeight: 1.5, color: N4, maxWidth: 660, textWrap: 'pretty' }}>{line}</div>
    </div>
  );
}

/* ── the piece ────────────────────────────────────────────────────── */
function Piece({ name }) {
  const { T, CUES, authoredTotal } = useComposition();
  const owner = name || 'Khalid Al-Amri';

  /* camera: [time, value] tracks over the phone screen */
  const zoom = MOTION.glide(T, [
    [0, 1], [CUES.Welcome, 1], [CUES.Products, 1], [CUES.Prices, 1],
    [CUES.Dashboard, 1], [CUES.Dashboard + 1.6, 1], [CUES.Billing - 0.3, 1],
    [CUES.PriceEdit + 0.6, 1], [CUES.PriceEdit + 1.5, 1.85], [CUES.PriceEdit + 4.2, 1.85],
    [CUES.Customer + 0.4, 1.1], [CUES.Payment + 0.6, 1.14], [CUES.Receipt - 0.3, 1],
    [CUES.Stock + 1.2, 1.12], [CUES.Restock - 0.3, 1], [CUES.Offline, 1], [CUES.Close, 1]
  ]);
  const camX = MOTION.glide(T, [
    [0, W / 2], [CUES.Dashboard + 1.6, W / 2], [CUES.Billing - 0.3, W / 2], [CUES.PriceEdit + 0.6, W / 2],
    [CUES.PriceEdit + 1.5, 300], [CUES.PriceEdit + 4.2, 300],
    [CUES.Customer + 0.4, W / 2], [CUES.Stock + 1.2, W / 2], [CUES.Restock - 0.3, W / 2]
  ]);
  const camY = MOTION.glide(T, [
    [0, H / 2], [CUES.Dashboard + 1.6, H / 2], [CUES.Billing - 0.3, H / 2], [CUES.PriceEdit + 0.6, H / 2],
    [CUES.PriceEdit + 1.5, 340], [CUES.PriceEdit + 4.2, 340],
    [CUES.Customer + 0.4, 620], [CUES.Payment + 0.6, 660], [CUES.Receipt - 0.3, H / 2],
    [CUES.Stock + 1.2, 420], [CUES.Restock - 0.3, H / 2]
  ]);

  /* cursor */
  const cx = MOTION.glide(T, [
    [CUES.Welcome + 0.5, 200], [CUES.Welcome + 0.85, 200], [CUES.Welcome + 2.9, 200], [CUES.Welcome + 3.3, 200],
    [CUES.Products + 0.9, 120], [CUES.Products + 1.6, 265], [CUES.Products + 2.35, 100], [CUES.Products + 3.1, 250],
    [CUES.Prices + 1, 210], [CUES.Prices + 4.2, 240],
    [CUES.Dashboard + 3.4, 200],
    [CUES.Billing + 0.6, 200], [CUES.Billing + 2.2, 200], [CUES.Billing + 3.6, 200], [CUES.Billing + 5.4, 300],
    [CUES.PriceEdit + 1.8, 330], [CUES.PriceEdit + 4.6, 330],
    [CUES.Customer + 1.2, 200], [CUES.Customer + 3.2, 200],
    [CUES.Payment + 1.2, 300], [CUES.Payment + 3.6, 200],
    [CUES.Stock + 2, 300], [CUES.Restock + 1.4, 200], [CUES.Restock + 4.2, 200],
    [CUES.Offline + 2, 330]
  ]);
  const cy = MOTION.glide(T, [
    [CUES.Welcome + 0.5, 500], [CUES.Welcome + 0.85, 300], [CUES.Welcome + 2.9, 300], [CUES.Welcome + 3.3, 800],
    [CUES.Products + 0.9, 470], [CUES.Products + 1.6, 470], [CUES.Products + 2.35, 500], [CUES.Products + 3.1, 500],
    [CUES.Prices + 1, 400], [CUES.Prices + 4.2, 810],
    [CUES.Dashboard + 3.4, 640],
    [CUES.Billing + 0.6, 150], [CUES.Billing + 2.2, 270], [CUES.Billing + 3.6, 470], [CUES.Billing + 5.4, 300],
    [CUES.PriceEdit + 1.8, 355], [CUES.PriceEdit + 4.6, 355],
    [CUES.Customer + 1.2, 700], [CUES.Customer + 3.2, 640],
    [CUES.Payment + 1.2, 720], [CUES.Payment + 3.6, 830],
    [CUES.Stock + 2, 430], [CUES.Restock + 1.4, 620], [CUES.Restock + 4.2, 810],
    [CUES.Offline + 2, 690]
  ]);
  const TAPS = [
    CUES.Welcome + 0.9, CUES.Welcome + 3.3,
    CUES.Products + 1.05, CUES.Products + 1.8, CUES.Products + 2.55, CUES.Products + 3.3,
    CUES.Prices + 4.3, CUES.Dashboard + 3.5,
    CUES.Billing + 2.3, CUES.Billing + 3.7, CUES.Billing + 5.5,
    CUES.PriceEdit + 1.9, CUES.Customer + 1.3, CUES.Customer + 3.3,
    CUES.Payment + 1.3, CUES.Payment + 3.7, CUES.Restock + 1.5, CUES.Restock + 4.3,
    CUES.Offline + 2.1
  ];
  let tap = 0;
  TAPS.forEach(t => { const p = clamp((T - t) / 0.5, 0, 1); if (p > 0 && p < 1) tap = p; });
  const cursorOn = T > CUES.Welcome + 0.3 && T < CUES.Close - 0.5;

  /* derived scene values */
  const price = T < CUES.PriceEdit + 2 ? 145 : 130;
  const edited = T > CUES.PriceEdit + 2;
  const customer = T < CUES.Customer + 3.3 ? typed('Ahm', T, CUES.Customer + 1.5, CUES.Customer + 2.6) : 'Ahmed Contracting';
  const hints = T > CUES.Customer + 1.3 && T < CUES.Customer + 3.3;
  const part = T > CUES.Payment + 1.3;
  const paid = typed('100', T, CUES.Payment + 1.8, CUES.Payment + 2.6);
  const total = 32 * 2 + price;
  const padStock = 58;
  const rQty = typed('50', T, CUES.Restock + 1.9, CUES.Restock + 2.4);
  const rCost = typed('17', T, CUES.Restock + 2.6, CUES.Restock + 3.0);
  const rSup = typed('Gulf Hardware Supply', T, CUES.Restock + 0.9, CUES.Restock + 1.8);

  const openP = MOTION.enter(T, 0.5, 2.2);
  const phoneY = (1 - openP) * 220 + (1 - MOTION.enter(T, 0, 0.6)) * 40;
  const closeP = MOTION.enter(T, CUES.Close, CUES.Close + 1.1);
  const phoneScale = 1 - closeP * 0.06;
  const panelX = 1010;

  return (
    <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(120% 90% at 18% -10%, #23203a 0%, #12141f 55%)', fontFamily: FONT, overflow: 'hidden' }}>
      {/* wordmark */}
      <div style={{ position: 'absolute', left: panelX, top: 96, display: 'flex', alignItems: 'center', gap: 11, opacity: 0.9 }}>
        <div style={{ width: 30, height: 30, borderRadius: 8, border: '1px solid ' + ACC, color: ACC, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 15 }}>◫</div>
        <div style={{ fontSize: 22, color: TX, letterSpacing: '-.02em' }}>Stockbook</div>
      </div>

      {/* chapter rail */}
      <div style={{ position: 'absolute', left: panelX, bottom: 110, display: 'flex', gap: 26, opacity: MOTION.enter(T, CUES.Welcome, CUES.Welcome + 0.8) * (1 - MOTION.enter(T, CUES.Close - 0.3, CUES.Close + 0.3)) }}>
        {CHAPTERS.map(([label, cue], i) => {
          const start = CUES[cue];
          const end = i + 1 < CHAPTERS.length ? CUES[CHAPTERS[i + 1][1]] : CUES.Close;
          const on = T >= start && T < end;
          return (
            <div key={label} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ height: 2, width: 108, background: N8, position: 'relative' }}>
                <div style={{ position: 'absolute', inset: 0, width: (clamp((T - start) / (end - start), 0, 1) * 100) + '%', background: ACC }} />
              </div>
              <div style={{ fontSize: 13, color: on ? TX : N6, letterSpacing: '.02em' }}>{label}</div>
            </div>
          );
        })}
      </div>

      {/* panels */}
      <div style={{ position: 'absolute', left: panelX, top: 330, width: 780, height: 420 }}>
        <Shot from={0} to={CUES.Welcome}>
          <div style={{ opacity: MOTION.enter(T, 0.4, 1.4) }}>
            <div style={{ fontSize: 74, lineHeight: 1.02, letterSpacing: '-.035em', color: TX, marginBottom: 20 }}>Your shop,<br />in your pocket.</div>
            <div style={{ fontSize: 23, color: N4, maxWidth: 620, lineHeight: 1.5 }}>Stockbook keeps products, stock and prices straight — with no account, no signal and nothing leaving the phone.</div>
          </div>
        </Shot>
        <Shot from={CUES.Welcome} to={CUES.Dashboard}><Panel T={T} i={1} title="Set the shop up once" line="Your name, the products you carry, then the count on the shelf with what you paid and what you charge. Nothing opens until all three are in." from={CUES.Welcome} to={CUES.Dashboard} /></Shot>
        <Shot from={CUES.Dashboard} to={CUES.Billing}><Panel T={T} i={2} title="A day starts empty" line="Sales, bill count and a standing reminder that this data lives on one phone — back it up." from={CUES.Dashboard} to={CUES.Billing} /></Shot>
        <Shot from={CUES.Billing} to={CUES.PriceEdit}><Panel T={T} i={3} title="Bill by search, or browse the lot" line="Type a name when you know it. When you don't, open the full list and tap your way down it." from={CUES.Billing} to={CUES.PriceEdit} /></Shot>
        <Shot from={CUES.PriceEdit} to={CUES.Customer}><Panel T={T} i={4} title="The price is a suggestion" line="Your selling price fills in automatically — change it on the bill for a haggle or a trade discount, and the shop price stays put." from={CUES.PriceEdit} to={CUES.Customer} /></Shot>
        <Shot from={CUES.Customer} to={CUES.Receipt}><Panel T={T} i={5} title="Who bought it, what they paid" line="Every bill carries a name, suggested from people you've billed before. Take part of the money now and the balance is written down." from={CUES.Customer} to={CUES.Receipt} /></Shot>
        <Shot from={CUES.Receipt} to={CUES.Restock}><Panel T={T} i={6} title="Stock keeps itself" line="Saving a bill takes the pieces off the shelf. Sixty padlocks became fifty-eight without you counting anything." from={CUES.Receipt} to={CUES.Restock} /></Shot>
        <Shot from={CUES.Restock} to={CUES.Offline}><Panel T={T} i={7} title="Two ways to restock" line="Quick add when you top up a bin. A purchase entry when a supplier delivers — that one updates the buying price too." from={CUES.Restock} to={CUES.Offline} /></Shot>
        <Shot from={CUES.Offline} to={CUES.Close}><Panel T={T} i={8} title="Works with the signal off" line="No servers, no sync, no subscription. Write a backup file when it suits you and keep a copy somewhere safe." from={CUES.Offline} to={CUES.Close} /></Shot>
        <Shot from={CUES.Close} to={authoredTotal}>
          <div style={{ opacity: MOTION.enter(T, CUES.Close + 0.2, CUES.Close + 1) }}>
            <div style={{ fontSize: 74, lineHeight: 1.02, letterSpacing: '-.035em', color: TX, marginBottom: 20 }}>Stockbook</div>
            <div style={{ fontSize: 23, color: N4, maxWidth: 620, lineHeight: 1.5 }}>Products, stock, prices and bills. One phone, no signal, nothing to sign up for.</div>
          </div>
        </Shot>
      </div>

      {/* phone */}
      <div style={{ position: 'absolute', left: 300, top: 103, width: W, height: H, transform: 'translateY(' + phoneY + 'px) scale(' + phoneScale + ')', opacity: MOTION.enter(T, 0, 0.7) }}>
        <div style={{ position: 'absolute', inset: 0, borderRadius: 46, overflow: 'hidden', background: BG, boxShadow: '0 0 0 10px #23252f, 0 0 0 11px ' + N8 + ', 0 60px 110px rgba(0,0,0,.65)' }}>
          <div style={{ position: 'absolute', width: W, height: H, transformOrigin: '0 0', transform: 'translate(' + (W / 2 - camX * zoom) + 'px,' + (H / 2 - camY * zoom) + 'px) scale(' + zoom + ')' }}>
            <Shot from={0} to={CUES.Welcome}><ScWelcome T={T} c={CUES.Welcome} name="" /></Shot>
            <Shot from={CUES.Welcome} to={CUES.Products}><ScWelcome T={T} c={CUES.Welcome} name={owner} /></Shot>
            <Shot from={CUES.Products} to={CUES.Prices}><ScProducts T={T} c={CUES.Products} name={owner} /></Shot>
            <Shot from={CUES.Prices} to={CUES.Dashboard}><ScPrices T={T} c={CUES.Prices} /></Shot>
            <Shot from={CUES.Dashboard} to={CUES.Billing}><ScDash T={T} c={CUES.Dashboard} name={owner} sales={0} bills={0} due={0} backed={false} /></Shot>
            <Shot from={CUES.Billing} to={CUES.Billing + 3.2}><ScPicker T={T} c={CUES.Billing} query={typed('pad', T, CUES.Billing + 1.1, CUES.Billing + 2)} list={T > CUES.Billing + 1.4 ? [CATALOG[2]] : CATALOG} cartN={0} /></Shot>
            <Shot from={CUES.Billing + 3.2} to={CUES.Billing + 4.4}>
              <Sc><Head title="New bill" right="1 line" />
                <div style={{ padding: '0 20px 10px' }}><Field ph="Add a product…" h={42} /></div>
                <div style={{ padding: '0 20px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <CartLine name="Padlock" qty={2} price={32} base={32} stock={60} />
                  <Btn h={44} style={{ fontSize: 13.5 }}>⌕ Add another item</Btn>
                </div>
              </Sc>
            </Shot>
            <Shot from={CUES.Billing + 4.4} to={CUES.PriceEdit}><ScPicker T={T} c={CUES.Billing} query="" list={CATALOG} cartN={1} cartTotal={64} /></Shot>
            <Shot from={CUES.PriceEdit} to={CUES.Receipt}>
              <ScCart T={T} c={CUES.PriceEdit} price={price} edited={edited} customer={T > CUES.Customer + 1 ? customer : ''} hints={hints} part={part} paid={paid} total={total} />
            </Shot>
            <Shot from={CUES.Receipt} to={CUES.Stock}><ScReceipt T={T} c={CUES.Receipt} /></Shot>
            <Shot from={CUES.Stock} to={CUES.Restock}><ScItems T={T} c={CUES.Stock} padStock={padStock} /></Shot>
            <Shot from={CUES.Restock} to={CUES.Offline}><ScRestock T={T} c={CUES.Restock} qty={rQty} cost={rCost} supplier={rSup} /></Shot>
            <Shot from={CUES.Offline} to={CUES.Close + 99}>
              <ScDash T={T} c={CUES.Offline} name={owner} sales={194} bills={1} due={94} backed={T > CUES.Offline + 2.1} />
            </Shot>
            <Status offline={T > CUES.Offline} />
            {cursorOn && <Cursor x={cx} y={cy} tap={tap} />}
          </div>
          {/* dynamic island stays with the hardware */}
          <div style={{ position: 'absolute', top: 11, left: '50%', marginLeft: -63, width: 126, height: 37, borderRadius: 24, background: '#000', opacity: clamp(1 - (zoom - 1) * 3, 0, 1) }} />
          <div style={{ position: 'absolute', bottom: 9, left: '50%', marginLeft: -69, width: 139, height: 5, borderRadius: 3, background: 'rgba(233,233,237,.55)', opacity: clamp(1 - (zoom - 1) * 3, 0, 1) }} />
        </div>
      </div>
    </div>
  );
}

function StockbookVideo() {
  const [t, setTweak] = useTweaks(window.TWEAK_DEFAULTS);
  return (
    <div style={{ width: '100%', height: '100%' }}>
      <CompositionStage width={1920} height={1080} scenes={window.OM_SCENES} playback={window.OM_PLAYBACK} bg="#12141f">
        <Piece name={t.ownerName} />
      </CompositionStage>
      <TweaksPanel>
        <TweakSection label="Story" />
        <TweakText label="Owner name" value={t.ownerName} onChange={v => setTweak('ownerName', v)} />
        <TweakSection label="Editing" />
        <TweakToggle label="Motion editor" value={t.motionEditor} onChange={v => setTweak('motionEditor', v)} />
      </TweaksPanel>
    </div>
  );
}
window.StockbookVideo = StockbookVideo;
window.Piece = Piece;
