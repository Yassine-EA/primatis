import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { CreateTagRequest } from '../models/create-tag-request';
import { TagResponse } from '../models/tag-response';
import { UpdateTagRequest } from '../models/update-tag-request';
import { StaffTagApiService } from './staff-tag-api.service';

describe('StaffTagApiService', () => {
  let service: StaffTagApiService;
  let httpTestingController: HttpTestingController;

  const tag: TagResponse = { id: 1, code: 'health', label: 'Santé', description: null };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });

    service = TestBed.inject(StaffTagApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  // ---------------------------------------------------------------
  // listTags
  // ---------------------------------------------------------------

  it('should GET /api/v1/staff/tags with default page/size params', () => {
    service.listTags().subscribe();

    const request = httpTestingController.expectOne((req) => req.url === '/api/v1/staff/tags' && req.method === 'GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');

    request.flush({ content: [tag], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('should GET /api/v1/staff/tags with explicit page/size params', () => {
    service.listTags(4, 100).subscribe();

    const request = httpTestingController.expectOne((req) => req.url === '/api/v1/staff/tags' && req.method === 'GET');
    expect(request.request.params.get('page')).toBe('4');
    expect(request.request.params.get('size')).toBe('100');

    request.flush({ content: [], page: 4, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('should propagate the PageResponse<TagResponse> returned by the backend on listTags', () => {
    let received: unknown;
    service.listTags().subscribe((value) => (received = value));

    httpTestingController
      .expectOne((req) => req.url === '/api/v1/staff/tags' && req.method === 'GET')
      .flush({ content: [tag], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual({ content: [tag], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  // ---------------------------------------------------------------
  // createTag
  // ---------------------------------------------------------------

  it('should POST /api/v1/staff/tags with the exact CreateTagRequest body', () => {
    const requestBody: CreateTagRequest = { code: 'health', label: 'Santé' };
    service.createTag(requestBody).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/tags');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);

    request.flush(tag);
  });

  it('should propagate the TagResponse returned by the backend on createTag', () => {
    let received: TagResponse | undefined;
    service.createTag({ code: 'health', label: 'Santé' }).subscribe((value) => (received = value));

    httpTestingController.expectOne('/api/v1/staff/tags').flush(tag);

    expect(received).toEqual(tag);
  });

  // ---------------------------------------------------------------
  // updateTag
  // ---------------------------------------------------------------

  it('should PATCH /api/v1/staff/tags/{tagId} with the exact UpdateTagRequest body, never a code field', () => {
    const requestBody: UpdateTagRequest = { label: 'Nouveau label' };
    service.updateTag(1, requestBody).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/tags/1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ label: 'Nouveau label' });
    expect(Object.prototype.hasOwnProperty.call(request.request.body, 'code')).toBe(false);

    request.flush({ ...tag, label: 'Nouveau label' });
  });

  it('should send an explicit null description as null in the JSON body, never omitted (PATCH sparse)', () => {
    const requestBody: UpdateTagRequest = { description: null };
    service.updateTag(1, requestBody).subscribe();

    const request = httpTestingController.expectOne('/api/v1/staff/tags/1');
    expect(request.request.body).toEqual({ description: null });
    expect(Object.prototype.hasOwnProperty.call(request.request.body, 'description')).toBe(true);

    request.flush(tag);
  });

  it('should propagate the TagResponse returned by the backend on updateTag', () => {
    let received: TagResponse | undefined;
    service.updateTag(1, { label: 'Nouveau label' }).subscribe((value) => (received = value));

    const updated = { ...tag, label: 'Nouveau label' };
    httpTestingController.expectOne('/api/v1/staff/tags/1').flush(updated);

    expect(received).toEqual(updated);
  });

  // ---------------------------------------------------------------
  // deleteTag
  // ---------------------------------------------------------------

  it('should DELETE /api/v1/staff/tags/{tagId} and expect no response body', () => {
    let completed = false;
    service.deleteTag(1).subscribe(() => (completed = true));

    const request = httpTestingController.expectOne('/api/v1/staff/tags/1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBe(true);
  });
});
