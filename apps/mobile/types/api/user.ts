export type Role = 'USER' | 'MEMBER' | 'ADMIN';

export interface UserResponse {
  id: number;
  nickname: string;
  role: Role;
}
