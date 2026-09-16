import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { GeorgesLemaitrePage } from './georges-lemaitre';

describe('GeorgesLemaitrePage', () => {
  let fixture: ComponentFixture<GeorgesLemaitrePage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GeorgesLemaitrePage],
      providers: [provideRouter([])],
    });

    fixture = TestBed.createComponent(GeorgesLemaitrePage);
    fixture.detectChanges();
  });

  it('should render exactly one h1 carrying the name', () => {
    const headings: HTMLHeadingElement[] = fixture.nativeElement.querySelectorAll('h1');
    expect(headings.length).toBe(1);
    expect(headings[0].textContent).toContain('Georges Lemaître');
  });

  it('should render the main biographical sections as h2, in a coherent editorial order', () => {
    const headings: HTMLHeadingElement[] = Array.from(fixture.nativeElement.querySelectorAll('h2'));
    const text = headings.map((heading) => heading.textContent?.trim());
    expect(text).toEqual([
      'Repères biographiques',
      "L'Univers en expansion",
      "L'atome primitif",
      'Georges Lemaître et Charleroi',
      'Pourquoi PRIMATIS porte son nom ?',
      'Sources',
    ]);
  });

  it('should set the document title (DESIGN-V2-B5)', () => {
    expect(document.title).toBe('Georges Lemaître — PRIMATIS');
  });

  it('should render the portrait with a sober alt text and no religious framing', () => {
    const img: HTMLImageElement = fixture.nativeElement.querySelector('.lemaitre-hero-portrait');
    expect(img.getAttribute('src')).toBe(
      '/assets/public/georges-lemaitre/hero/georges-lemaitre-portrait-1024.webp',
    );
    expect(img.getAttribute('alt')).toContain('Georges Lemaître');
    expect(img.getAttribute('alt')).toContain('illustré');
  });

  it('should never claim he invented the Big Bang', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text.toLowerCase()).not.toContain('a inventé le big bang');
  });

  it('should keep the historically correct order: 1927 expansion, 1931 primeval atom (contradicts the mockup, matches the already-sourced content)', () => {
    const text: string = fixture.nativeElement.textContent;
    const expansionIndex = text.indexOf("L'Univers en expansion");
    const atomIndex = text.indexOf("L'atome primitif");
    expect(text).toContain('1927');
    expect(text).toContain('1931');
    // La section "Univers en expansion" (1927) doit précéder "L'atome
    // primitif" (1931) dans le DOM — jamais l'inverse montré par la maquette.
    expect(expansionIndex).toBeGreaterThan(-1);
    expect(atomIndex).toBeGreaterThan(expansionIndex);
  });

  it('should never render any quote attributed to Georges Lemaître (no documented source in this repository)', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).not.toContain('Un même ciel nous rassemble');
    expect(text).not.toContain('Entre les livres et les étoiles');
    expect(text).not.toContain('plus étrange que nous ne pouvons le penser');
    expect(fixture.nativeElement.querySelector('blockquote')).toBeNull();
    expect(fixture.nativeElement.querySelector('cite')).toBeNull();
  });

  it('should never invent an availability, event, or resource list without a real backing contract', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).not.toContain('exemplaire');
    expect(text).not.toContain('Événement');
  });

  it('should cite the UCLouvain, ESA and Wikimedia sources', () => {
    expect(
      fixture.nativeElement.querySelector(
        'a[href="https://archives.uclouvain.be/exhibits/show/georges-lemaitre"]',
      ),
    ).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector(
        'a[href="https://www.esa.int/Space_in_Member_States/Belgium_-_Francais/Le_lancement_de_l_ATV-5_belge_Georges_Lemaitre"]',
      ),
    ).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector(
        'a[href="https://commons.wikimedia.org/wiki/File:GLemaitre30.jpg"]',
      ),
    ).not.toBeNull();
  });

  it('should link to the real public Catalogue and provide an in-page anchor to the biographical timeline', () => {
    expect(fixture.nativeElement.querySelector('a[href="/catalogue"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="#lemaitre-reperes"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#lemaitre-reperes')).not.toBeNull();
  });

  it('should provide a link back to the home page', () => {
    expect(fixture.nativeElement.querySelector('a[href="/"]')).not.toBeNull();
  });

  it('should scroll to the biographical timeline and prevent the default anchor navigation (avoids the <base href> fragment-resolution bug)', () => {
    const anchor: HTMLAnchorElement = fixture.nativeElement.querySelector(
      'a[href="#lemaitre-reperes"]',
    );
    const target: HTMLElement = fixture.nativeElement.querySelector('#lemaitre-reperes');
    const scrollIntoViewSpy = vi.fn();
    target.scrollIntoView = scrollIntoViewSpy;
    const event = new MouseEvent('click', { cancelable: true });
    const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

    anchor.dispatchEvent(event);

    expect(preventDefaultSpy).toHaveBeenCalled();
    expect(scrollIntoViewSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
  });

  it('should render the validated timeline years', () => {
    const years: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.lemaitre-timeline-year'),
    );
    expect(years.map((el) => el.textContent?.trim())).toEqual([
      '1894',
      '1927',
      '1931',
      '1934',
      '1966',
    ]);
  });
});
