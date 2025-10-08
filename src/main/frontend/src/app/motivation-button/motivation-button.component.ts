import { Component, signal, inject, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { trigger, state, style, transition, animate } from '@angular/animations';

@Component({
  selector: 'app-motivation-button',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './motivation-button.component.html',
  styleUrl: './motivation-button.component.css',
  animations: [
    trigger('slideInOut', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translate(-50%, -50%) scale(0.8)' }),
        animate('300ms ease-in', style({ opacity: 1, transform: 'translate(-50%, -50%) scale(1)' }))
      ]),
      transition(':leave', [
        animate('300ms ease-out', style({ opacity: 0, transform: 'translate(-50%, -50%) scale(0.8)' }))
      ])
    ])
  ]
})
export class MotivationButtonComponent {
  private readonly httpClient = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  // Predefined motivational messages
  private readonly predefinedMessages = [
    "You've got this! 💪",
    "Keep pushing forward! 🚀",
    "Every step counts! 👣",
    "Believe in yourself! ✨",
    "You're doing amazing! 🌟"
  ];

  // Signals for reactive state
  readonly currentMessage = signal<string>('');
  readonly isShowingMessage = signal<boolean>(false);
  readonly clickCount = signal<number>(0);

  private messageTimeout: any;

  constructor() {
    // Initialize with first message
    this.currentMessage.set(this.predefinedMessages[0]);
  }

  onMotivationClick(): void {
    const currentCount = this.clickCount();
    const newCount = currentCount + 1;
    this.clickCount.set(newCount);

    // Clear any existing timeout
    if (this.messageTimeout) {
      clearTimeout(this.messageTimeout);
    }

    // Determine which message to show
    if (newCount % 5 === 0) {
      // Every 5th click, get AI-generated message
      this.getAiMotivationMessage();
    } else {
      // Use predefined messages
      const messageIndex = (newCount - 1) % this.predefinedMessages.length;
      this.showMessage(this.predefinedMessages[messageIndex]);
    }
  }

  private showMessage(message: string): void {
    this.currentMessage.set(message);
    this.isShowingMessage.set(true);

    // Hide message after 5 seconds
    this.messageTimeout = setTimeout(() => {
      this.isShowingMessage.set(false);
    }, 5000);
  }

  private getAiMotivationMessage(): void {
    // Call backend API to get AI-generated motivational message
    this.httpClient.post<{ message: string }>('/api/motivation/generate', {})
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.showMessage(response.message);
        },
        error: (error) => {
          console.error('Error getting AI motivation message:', error);
          // Fallback to predefined message if AI fails
          const fallbackIndex = (this.clickCount() - 1) % this.predefinedMessages.length;
          this.showMessage(this.predefinedMessages[fallbackIndex]);
        }
      });
  }
}
