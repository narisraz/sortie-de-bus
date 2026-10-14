#!/usr/bin/env python3
"""Génère icone.svg (1024x1024) et ses variantes ; même dessin que drawBus (GameView.swift)."""
import math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))

def rr(x, y, w, h, r, fill, op=None):
    o = f' fill-opacity="{op}"' if op is not None else ''
    return f'<rect x="{x:.2f}" y="{y:.2f}" width="{w:.2f}" height="{h:.2f}" rx="{r:.2f}" fill="#{fill}"{o}/>'

def bus(cx, cy, wid, ln, c, d, l, ang=0):
    r = wid * 0.28
    s = [rr(-wid/2 + wid*0.08, -ln/2 + wid*0.14, wid, ln, r, '000000', 0.35),
         rr(-wid/2, -ln/2, wid, ln, r, d),
         rr(-wid/2, -ln/2, wid, ln - wid*0.1, r, c)]
    n = max(2, round(ln / wid * 1.4))
    gap = ln * 0.62 / n
    for i in range(n):
        yy = -ln/2 + ln*0.27 + i*gap
        s.append(rr(-wid/2 + wid*0.04, yy, wid*0.09, gap*0.7, wid*0.03, 'd2ebff', 0.85))
        s.append(rr(wid/2 - wid*0.13, yy, wid*0.09, gap*0.7, wid*0.03, 'd2ebff', 0.85))
    s.append(rr(-wid/2 + wid*0.12, -ln/2 + wid*0.07, wid*0.76, wid*0.24, wid*0.08, 'd9efff'))
    s.append(rr(-wid/2 + wid*0.18, -ln/2 + wid*0.1, wid*0.22, wid*0.08, wid*0.03, 'ffffff', 0.7))
    s.append(rr(-wid*0.3, -ln/2 + wid*0.42, wid*0.6, ln - wid*0.62, wid*0.14, l))
    # grosse flèche vers le haut (proportions du jeu, un peu élargie)
    aw, ah, ay = wid*0.5, min(ln*0.55, wid*1.25), wid*0.12
    t, sh, st = ay - ah/2, aw*0.55, aw*0.18
    pts = [(0, t), (aw/2, t+sh), (st, t+sh), (st, ay+ah/2), (-st, ay+ah/2), (-st, t+sh), (-aw/2, t+sh)]
    s.append('<polygon fill="#ffffff" points="' + ' '.join(f'{x:.2f},{y:.2f}' for x, y in pts) + '"/>')
    s.append(rr(-wid/2 + wid*0.1, -ln/2 + 2, wid*0.14, wid*0.05, 0, 'fff6c2'))
    s.append(rr(wid/2 - wid*0.24, -ln/2 + 2, wid*0.14, wid*0.05, 0, 'fff6c2'))
    return f'<g transform="translate({cx} {cy}) rotate({ang})">' + ''.join(s) + '</g>'

def background():
    s = ['<rect width="1024" height="1024" fill="url(#bg)"/>']
    s += [f'<rect x="{x}" y="0" width="14" height="1024" fill="#ffffff" fill-opacity="0.18"/>' for x in (70, 285, 500, 715, 930)]
    return ''.join(s)

def buses():
    return (bus(300, 600, 150, 330, '2f80ed', '1c56a8', '7fb3ff') +
            bus(735, 560, 150, 330, '27ae60', '1a7a43', '7be0a5') +
            bus(520, 470, 230, 520, 'f2802c', 'b5561a', 'ffb57f', 18))

DEFS = ('<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
        '<stop offset="0" stop-color="#5a6576"/><stop offset="1" stop-color="#3a4250"/></linearGradient>'
        '<clipPath id="sq"><rect x="100" y="100" width="824" height="824" rx="185"/></clipPath></defs>')

def svg(body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">{DEFS}{body}</svg>\n'

full = background() + '<rect x="0" y="880" width="1024" height="22" fill="#f2c94c"/>' + buses()
variants = {
    'icone.svg': svg(full),
    'mac.svg': svg(f'<g clip-path="url(#sq)"><g transform="translate(100 100) scale({824/1024})">{full}</g></g>'),
    'android-fond.svg': svg(background()),
    'android-avant.svg': svg(f'<g transform="translate(512 512) scale(0.64) translate(-512 -512)">{buses()}</g>'),
}
for name, content in variants.items():
    open(os.path.join(HERE, name), 'w').write(content)
print('ok')
